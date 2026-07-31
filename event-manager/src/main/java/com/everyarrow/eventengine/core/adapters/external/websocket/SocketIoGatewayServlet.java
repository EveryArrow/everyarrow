/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.adapters.external.websocket;

import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.eventengine.core.adapters.external.http.JettyRestUtils;
import com.everyarrow.eventengine.core.hooks.SecurityBearerTokenVerifier;
import com.everyarrow.eventengine.core.hooks.SecurityProviderRegistry;
import com.everyarrow.eventengine.core.routing.ServiceCallFactory;
import com.everyarrow.eventengine.core.services.runtime.ServiceCall;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.everyarrow.eventengine.spi.model.ServiceCallResponse;
import com.everyarrow.eventengine.spi.services.RealtimeConnection;
import com.everyarrow.eventengine.spi.services.RealtimeGateway;
import com.everyarrow.utilities.environment.Environment;
import com.everyarrow.utilities.json.JsonUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.socket.engineio.server.EngineIoServer;
import io.socket.engineio.server.EngineIoServerOptions;
import io.socket.engineio.server.EngineIoWebSocket;
import io.socket.engineio.server.utils.ParseQS;
import io.socket.socketio.server.SocketIoNamespace;
import io.socket.socketio.server.SocketIoServer;
import io.socket.socketio.server.SocketIoSocket;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.ee10.websocket.server.JettyWebSocketServlet;
import org.eclipse.jetty.ee10.websocket.server.JettyWebSocketServletFactory;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * The single Socket.IO transport owned by Event Manager.
 *
 * <p>Connections are accepted only over WebSocket, require a bearer token in
 * the Socket.IO v4 auth payload, and are revalidated for the lifetime of the
 * connection. Plugins receive only authenticated, resource-bounded
 * {@link RealtimeConnection} instances.</p>
 */
public final class SocketIoGatewayServlet extends JettyWebSocketServlet implements RealtimeGateway {

    static final String MAX_TEXT_MESSAGE_BYTES_CONFIG = "EA_SOCKET_IO_MAX_TEXT_MESSAGE_BYTES";
    static final String IDLE_TIMEOUT_SECONDS_CONFIG = "EA_SOCKET_IO_IDLE_TIMEOUT_SECONDS";
    static final String MAX_CONNECTIONS_CONFIG = "EA_SOCKET_IO_MAX_CONNECTIONS";
    static final String MAX_EVENTS_PER_MINUTE_CONFIG = "EA_SOCKET_IO_MAX_EVENTS_PER_MINUTE";
    static final String MAX_ROOMS_PER_CONNECTION_CONFIG = "EA_SOCKET_IO_MAX_ROOMS_PER_CONNECTION";
    static final String TOKEN_REVALIDATION_SECONDS_CONFIG = "EA_SOCKET_IO_TOKEN_REVALIDATION_SECONDS";
    static final int DEFAULT_MAX_TEXT_MESSAGE_BYTES = 64 * 1024;
    static final long DEFAULT_IDLE_TIMEOUT_SECONDS = 60;
    static final int DEFAULT_MAX_CONNECTIONS = 500;
    static final int DEFAULT_MAX_EVENTS_PER_MINUTE = 120;
    static final int DEFAULT_MAX_ROOMS_PER_CONNECTION = 32;
    static final long DEFAULT_TOKEN_REVALIDATION_SECONDS = 30;

    private static final Logger logger = LoggerFactory.getLogger(SocketIoGatewayServlet.class);
    private static final String AUTH_EVENT = "authentication";
    private static final String ERROR_EVENT = "realtime:error";

    private final Engine engine;
    private final SecurityProviderRegistry securityProviders;
    private final EngineIoServer engineIoServer;
    private final SocketIoNamespace namespace;
    private final ScheduledExecutorService tokenScheduler;
    private final CopyOnWriteArrayList<Consumer<RealtimeConnection>> connectionListeners =
            new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, SocketIoRealtimeConnection> connections =
            new ConcurrentHashMap<>();
    private final AtomicInteger connectionCount = new AtomicInteger();
    private final AtomicBoolean stopped = new AtomicBoolean();
    private final int maxTextMessageBytes;
    private final long idleTimeoutSeconds;
    private final int maxConnections;
    private final int maxEventsPerMinute;
    private final int maxRoomsPerConnection;
    private final long tokenRevalidationSeconds;

    public SocketIoGatewayServlet(Engine engine, SecurityProviderRegistry securityProviders) {
        this.engine = require(engine, "engine");
        this.securityProviders = require(securityProviders, "securityProviders");
        this.maxTextMessageBytes = positiveIntConfig(
                MAX_TEXT_MESSAGE_BYTES_CONFIG, DEFAULT_MAX_TEXT_MESSAGE_BYTES);
        this.idleTimeoutSeconds = positiveLongConfig(
                IDLE_TIMEOUT_SECONDS_CONFIG, DEFAULT_IDLE_TIMEOUT_SECONDS);
        this.maxConnections = positiveIntConfig(MAX_CONNECTIONS_CONFIG, DEFAULT_MAX_CONNECTIONS);
        this.maxEventsPerMinute = positiveIntConfig(
                MAX_EVENTS_PER_MINUTE_CONFIG, DEFAULT_MAX_EVENTS_PER_MINUTE);
        this.maxRoomsPerConnection = positiveIntConfig(
                MAX_ROOMS_PER_CONNECTION_CONFIG, DEFAULT_MAX_ROOMS_PER_CONNECTION);
        this.tokenRevalidationSeconds = positiveLongConfig(
                TOKEN_REVALIDATION_SECONDS_CONFIG, DEFAULT_TOKEN_REVALIDATION_SECONDS);

        EngineIoServerOptions options = EngineIoServerOptions.newFromDefault();
        options.setCorsHandlingDisabled(true);
        options.setAllowSyncPolling(false);
        options.setHandshakeInterceptor(SocketIoGatewayServlet::isAllowedHandshake);
        this.engineIoServer = new EngineIoServer(options);
        this.namespace = new SocketIoServer(engineIoServer).namespace("/");
        this.namespace.on("connection", args -> authenticateConnection((SocketIoSocket) args[0]));
        this.tokenScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ea-socket-token-revalidation");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void configure(JettyWebSocketServletFactory factory) {
        factory.setMaxTextMessageSize(maxTextMessageBytes);
        factory.setIdleTimeout(Duration.ofSeconds(idleTimeoutSeconds));
        factory.addMapping("/*", (request, response) -> new JettyEngineIoWebSocket(engineIoServer, maxTextMessageBytes));
    }

    @Override
    public void service(ServletRequest request, ServletResponse response) throws ServletException, IOException {
        if (!(request instanceof HttpServletRequest httpRequest)
                || !(response instanceof HttpServletResponse httpResponse)) {
            super.service(request, response);
            return;
        }

        if (JettyRestUtils.rejectDisallowedOrigin(httpRequest, httpResponse)) {
            return;
        }

        String upgrade = httpRequest.getHeader("Upgrade");
        String transport = httpRequest.getParameter("transport");
        if (!"websocket".equalsIgnoreCase(upgrade) || !"websocket".equalsIgnoreCase(transport)) {
            httpResponse.setStatus(426);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{\"error\":\"Socket.IO WebSocket transport is required\"}");
            return;
        }

        super.service(request, response);
    }

    @Override
    public void addConnectionListener(Consumer<RealtimeConnection> listener) {
        connectionListeners.add(require(listener, "listener"));
    }

    @Override
    public void removeConnectionListener(Consumer<RealtimeConnection> listener) {
        if (listener != null) {
            connectionListeners.remove(listener);
        }
    }

    @Override
    public void broadcast(String room, String event, Object... payload) {
        requireRoomName(room);
        requireName(event, "event");
        requireOutboundSize(payload);
        namespace.broadcast(room, event, payload == null ? new Object[0] : payload);
    }

    @Override
    public Collection<RealtimeConnection> connectionsInRoom(String room) {
        requireRoomName(room);
        return java.util.Arrays.stream(namespace.getAdapter().listClients(room))
                .map(socket -> connections.get(socket.getId()))
                .filter(connection -> connection != null)
                .map(connection -> (RealtimeConnection) connection)
                .toList();
    }

    @Override
    public void destroy() {
        shutdown();
        super.destroy();
    }

    public void shutdown() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        connections.values().forEach(SocketIoRealtimeConnection::disconnect);
        connections.clear();
        connectionListeners.clear();
        tokenScheduler.shutdownNow();
        engineIoServer.shutdown();
    }

    private void authenticateConnection(SocketIoSocket socket) {
        if (connectionCount.incrementAndGet() > maxConnections) {
            connectionCount.decrementAndGet();
            reject(socket, "Connection limit reached");
            return;
        }

        SocketIoRealtimeConnection connection = null;
        try {
            String token = bearerToken(socket.getConnectData());
            SecurityBearerTokenVerifier.VerifiedBearerToken verified = securityProviders.verify(token);
            connection = new SocketIoRealtimeConnection(socket, token, verified);
            connections.put(socket.getId(), connection);
            SocketIoRealtimeConnection authenticatedConnection = connection;
            socket.on("disconnect", args -> removeConnection(authenticatedConnection));
            socket.registerAllEventListener(
                    (event, args) -> routeRegisteredEvent(authenticatedConnection, event, args));
            scheduleTokenRevalidation(authenticatedConnection);

            for (Consumer<RealtimeConnection> listener : connectionListeners) {
                listener.accept(authenticatedConnection);
            }
            socket.send(AUTH_EVENT, "{\"status\":\"success\"}");
        } catch (Exception error) {
            if (connection == null || !connections.containsKey(socket.getId())) {
                connectionCount.decrementAndGet();
            } else {
                removeConnection(connection);
            }
            logger.warn("Rejected Socket.IO connection {}: {}", socket.getId(), safeMessage(error));
            reject(socket, "Authentication failed");
        }
    }

    private void scheduleTokenRevalidation(SocketIoRealtimeConnection connection) {
        connection.tokenMonitor = tokenScheduler.scheduleAtFixedRate(() -> {
            try {
                securityProviders.verify(connection.bearerToken);
            } catch (Exception error) {
                logger.warn("Socket.IO token revalidation failed for {}", connection.getId());
                connection.send(AUTH_EVENT, "{\"status\":\"error\",\"message\":\"Authentication expired\"}");
                connection.disconnect();
            }
        }, tokenRevalidationSeconds, tokenRevalidationSeconds, TimeUnit.SECONDS);
    }

    private void routeRegisteredEvent(SocketIoRealtimeConnection connection, String event, Object[] args) {
        ServiceCallDefinition definition = engine.getRegistry().findWebSocket(event);
        if (definition == null) {
            return;
        }
        if (!connection.acceptInbound(event, args)) {
            return;
        }

        try {
            JsonObject payload = payload(args);
            ServiceCall call = ServiceCallFactory.fromWebSocket(
                    payload,
                    definition.getServiceType(),
                    event,
                    Map.of(
                            "http.authorization", "Bearer " + connection.bearerToken,
                            "realtime.connectionId", connection.getId(),
                            "websocket.event", event));
            ServiceCallResponse response = engine.execute(call);
            connection.send(event + ":response", JsonUtils.getGson().toJson(response));
        } catch (Exception error) {
            logger.warn("Socket.IO service call failed for event {}: {}", event, safeMessage(error));
            connection.send(ERROR_EVENT, errorPayload(event, "Event could not be processed"));
        }
    }

    private void removeConnection(SocketIoRealtimeConnection connection) {
        if (connections.remove(connection.getId(), connection)) {
            connection.cancelTokenMonitor();
            connectionCount.decrementAndGet();
        }
    }

    private void reject(SocketIoSocket socket, String message) {
        try {
            socket.send(AUTH_EVENT, errorPayload(AUTH_EVENT, message));
        } finally {
            socket.disconnect(true);
        }
    }

    static String bearerToken(Object connectData) {
        if (connectData == null) {
            throw new IllegalArgumentException("Missing Socket.IO auth payload");
        }

        JsonElement parsed;
        if (connectData instanceof String text) {
            try {
                parsed = JsonParser.parseString(text);
            } catch (Exception ignored) {
                throw new IllegalArgumentException("Socket.IO auth payload must be an object");
            }
        } else if ("org.json.JSONObject".equals(connectData.getClass().getName())) {
            parsed = JsonParser.parseString(connectData.toString());
        } else {
            parsed = JsonUtils.getGson().toJsonTree(connectData);
        }

        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("Socket.IO auth payload must be an object");
        }
        JsonObject auth = parsed.getAsJsonObject();
        JsonElement token = auth.get("token");
        if (token == null || !token.isJsonPrimitive() || token.getAsString().isBlank()) {
            throw new IllegalArgumentException("Missing Socket.IO bearer token");
        }
        return token.getAsString().trim();
    }

    static boolean isAllowedHandshake(Map<String, String> query, Map<String, List<String>> headers) {
        String transport = query == null ? null : query.get("transport");
        if (!"websocket".equalsIgnoreCase(transport)) {
            return false;
        }
        String origin = firstHeader(headers, "Origin");
        return origin == null || origin.isBlank() || JettyRestUtils.isOriginAllowed(origin);
    }

    static int positiveIntConfig(String key, int defaultValue) {
        String configured = Environment.getInstance().get(key, "");
        if (configured == null || configured.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(configured.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    static long positiveLongConfig(String key, long defaultValue) {
        String configured = Environment.getInstance().get(key, "");
        if (configured == null || configured.isBlank()) {
            return defaultValue;
        }
        try {
            long parsed = Long.parseLong(configured.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static JsonObject payload(Object[] args) {
        if (args == null || args.length == 0 || args[0] == null) {
            return new JsonObject();
        }

        JsonElement value;
        if (args[0] instanceof String text) {
            try {
                value = JsonParser.parseString(text);
            } catch (Exception ignored) {
                value = JsonUtils.getGson().toJsonTree(text);
            }
        } else if ("org.json.JSONObject".equals(args[0].getClass().getName())
                || "org.json.JSONArray".equals(args[0].getClass().getName())) {
            value = JsonParser.parseString(args[0].toString());
        } else {
            value = JsonUtils.getGson().toJsonTree(args[0]);
        }

        if (value.isJsonObject()) {
            return value.getAsJsonObject();
        }
        JsonObject wrapped = new JsonObject();
        wrapped.add("value", value);
        return wrapped;
    }

    private static String firstHeader(Map<String, List<String>> headers, String name) {
        if (headers == null || name == null) {
            return null;
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (!name.equalsIgnoreCase(entry.getKey())
                    || entry.getValue() == null
                    || entry.getValue().isEmpty()) {
                continue;
            }
            return entry.getValue().getFirst();
        }
        return null;
    }

    private static String errorPayload(String event, String message) {
        JsonObject payload = new JsonObject();
        payload.addProperty("status", "error");
        payload.addProperty("event", event);
        payload.addProperty("message", message);
        return payload.toString();
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static void requireName(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        if (value.length() > 256 || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }

    private static void requireRoomName(String room) {
        requireName(room, "room");
    }

    private final class SocketIoRealtimeConnection implements RealtimeConnection {
        private final SocketIoSocket socket;
        private final String bearerToken;
        private final SecurityBearerTokenVerifier.VerifiedBearerToken verifiedToken;
        private final Set<String> rooms = ConcurrentHashMap.newKeySet();
        private final AtomicInteger eventCount = new AtomicInteger();
        private volatile long eventWindowStartedAt = System.currentTimeMillis();
        private volatile java.util.concurrent.ScheduledFuture<?> tokenMonitor;

        private SocketIoRealtimeConnection(
                SocketIoSocket socket,
                String bearerToken,
                SecurityBearerTokenVerifier.VerifiedBearerToken verifiedToken) {
            this.socket = socket;
            this.bearerToken = bearerToken;
            this.verifiedToken = verifiedToken;
        }

        @Override
        public String getId() {
            return socket.getId();
        }

        @Override
        public String getAuthProviderId() {
            return verifiedToken.authProviderId();
        }

        @Override
        public String getEmail() {
            return verifiedToken.email();
        }

        @Override
        public void on(String event, Consumer<Object[]> listener) {
            requireName(event, "event");
            require(listener, "listener");
            socket.on(event, args -> {
                if (acceptInbound(event, args)) {
                    listener.accept(args);
                }
            });
        }

        @Override
        public void onDisconnect(Runnable listener) {
            require(listener, "listener");
            socket.on("disconnect", args -> listener.run());
        }

        @Override
        public void send(String event, Object... payload) {
            requireName(event, "event");
            requireOutboundSize(payload);
            socket.send(event, payload == null ? new Object[0] : payload);
        }

        @Override
        public void joinRoom(String room) {
            requireRoomName(room);
            if (!rooms.contains(room) && rooms.size() >= maxRoomsPerConnection) {
                throw new IllegalStateException("Realtime room limit reached");
            }
            rooms.add(room);
            socket.joinRoom(room);
        }

        @Override
        public void leaveRoom(String room) {
            requireRoomName(room);
            rooms.remove(room);
            socket.leaveRoom(room);
        }

        @Override
        public void disconnect() {
            cancelTokenMonitor();
            socket.disconnect(true);
            removeConnection(this);
        }

        private boolean acceptInbound(String event, Object[] args) {
            if (payloadBytes(args) > maxTextMessageBytes) {
                send(ERROR_EVENT, errorPayload(event, "Event payload is too large"));
                disconnect();
                return false;
            }

            long now = System.currentTimeMillis();
            if (now - eventWindowStartedAt >= TimeUnit.MINUTES.toMillis(1)) {
                synchronized (this) {
                    if (now - eventWindowStartedAt >= TimeUnit.MINUTES.toMillis(1)) {
                        eventWindowStartedAt = now;
                        eventCount.set(0);
                    }
                }
            }
            if (eventCount.incrementAndGet() > maxEventsPerMinute) {
                send(ERROR_EVENT, errorPayload(event, "Event rate limit exceeded"));
                disconnect();
                return false;
            }
            return true;
        }

        private void cancelTokenMonitor() {
            java.util.concurrent.ScheduledFuture<?> monitor = tokenMonitor;
            if (monitor != null) {
                monitor.cancel(false);
                tokenMonitor = null;
            }
        }
    }

    private static int payloadBytes(Object[] args) {
        if (args == null || args.length == 0) {
            return 0;
        }
        return JsonUtils.getGson().toJson(args).getBytes(StandardCharsets.UTF_8).length;
    }

    private void requireOutboundSize(Object[] payload) {
        if (payloadBytes(payload) > maxTextMessageBytes) {
            throw new IllegalArgumentException("Realtime payload exceeds configured message limit");
        }
    }

    public static final class JettyEngineIoWebSocket
            extends EngineIoWebSocket
            implements Session.Listener.AutoDemanding {
        private final EngineIoServer engineIoServer;
        private final int maxTextMessageBytes;
        private Session session;
        private Map<String, String> query;
        private Map<String, List<String>> headers;

        JettyEngineIoWebSocket(EngineIoServer engineIoServer, int maxTextMessageBytes) {
            this.engineIoServer = engineIoServer;
            this.maxTextMessageBytes = maxTextMessageBytes;
        }

        @Override
        public void onWebSocketOpen(Session session) {
            this.session = session;
            this.query = ParseQS.decode(session.getUpgradeRequest().getQueryString());
            this.headers = session.getUpgradeRequest().getHeaders();
            engineIoServer.handleWebSocket(this);
        }

        @Override
        public void onWebSocketClose(int statusCode, String reason) {
            emit("close");
            session = null;
        }

        @Override
        public void onWebSocketError(Throwable cause) {
            emit("error", "websocket error", cause == null ? "unknown" : cause.getMessage());
        }

        @Override
        public void onWebSocketText(String message) {
            if (message != null
                    && message.getBytes(StandardCharsets.UTF_8).length <= maxTextMessageBytes) {
                emit("message", (Object) message);
                return;
            }
            close(1009, "Message too large");
        }

        @Override
        public void onWebSocketBinary(ByteBuffer payload, Callback callback) {
            callback.fail(new IllegalArgumentException("Binary Socket.IO messages are not supported"));
            close(1003, "Binary messages are not supported");
        }

        @Override
        public Map<String, String> getQuery() {
            return query;
        }

        @Override
        public Map<String, List<String>> getConnectionHeaders() {
            return headers;
        }

        @Override
        public void write(String message) throws IOException {
            if (session == null) {
                throw new IOException("WebSocket session is closed");
            }
            session.sendText(message, Callback.NOOP);
        }

        @Override
        public void write(byte[] message) throws IOException {
            throw new IOException("Binary Socket.IO messages are not supported");
        }

        @Override
        public void close() {
            close(1000, "Closed");
        }

        private void close(int statusCode, String reason) {
            if (session != null) {
                session.close(statusCode, reason, Callback.NOOP);
            }
        }
    }
}
