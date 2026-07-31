package com.everyarrow.eventengine.tests.websocket;

import com.everyarrow.eventengine.core.hooks.SecurityBearerTokenVerifier;
import com.everyarrow.eventengine.core.hooks.SecurityPrincipalResolver;
import com.everyarrow.eventengine.core.hooks.SecurityProviderRegistry;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.everyarrow.eventengine.tests.EngineHarness;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SocketIoRouteIntegrationTest {

    private static final String TOKEN = "socket-io-integration-token";

    private EngineHarness harness;
    private HttpClient client;

    @BeforeAll
    void startHarness() throws Exception {
        harness = EngineHarness.start();
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        SecurityProviderRegistry securityProviders = harness.getEngine()
                .getPluginContext()
                .getService(SecurityProviderRegistry.class);
        securityProviders.install(
                token -> {
                    if (!TOKEN.equals(token)) {
                        throw new IllegalArgumentException("Unexpected integration token");
                    }
                    return new SecurityBearerTokenVerifier.VerifiedBearerToken(
                            "socket-user",
                            "socket-auth-provider",
                            null,
                            "socket-user",
                            "socket@example.com");
                },
                new FixedPrincipalResolver(),
                request -> true);
    }

    @AfterAll
    void stopHarness() {
        if (harness != null) {
            harness.close();
        }
    }

    @Test
    void authenticatesAndRoutesAnnotatedSocketIoEvent() throws Exception {
        ServiceCallDefinition definition =
                harness.getEngine().getRegistry().findWebSocket("/ws/tests/echo");
        assertNotNull(definition);
        assertEquals("/ws/tests/echo", definition.getWebSocketRoute().path);

        PacketListener listener = new PacketListener();
        WebSocket socket = client.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(
                        URI.create("ws://localhost:" + harness.getHttpPort()
                                + "/socket.io/?EIO=4&transport=websocket"),
                        listener)
                .get(5, TimeUnit.SECONDS);

        assertTrue(listener.awaitPacket("0").startsWith("0"));

        socket.sendText("40{\"token\":\"" + TOKEN + "\"}", true)
                .get(5, TimeUnit.SECONDS);
        assertTrue(listener.awaitPacket("40").startsWith("40"));

        JsonArray authentication = socketIoEvent(listener.awaitEvent("authentication"));
        JsonObject authenticationBody =
                JsonParser.parseString(authentication.get(1).getAsString()).getAsJsonObject();
        assertEquals("success", authenticationBody.get("status").getAsString());

        socket.sendText(
                        "42[\"/ws/tests/echo\",{\"message\":\"hello over socket.io\"}]",
                        true)
                .get(5, TimeUnit.SECONDS);

        JsonArray responseEvent = socketIoEvent(
                listener.awaitEvent("/ws/tests/echo:response"));
        JsonObject response =
                JsonParser.parseString(responseEvent.get(1).getAsString()).getAsJsonObject();
        assertEquals("webSocketEcho", response.get("serviceType").getAsString());
        assertEquals(200, response.get("httpStatus").getAsInt());
        assertEquals(
                "hello over socket.io",
                response.getAsJsonObject("body")
                        .getAsJsonObject("echo")
                        .get("message")
                        .getAsString());

        socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS);
    }

    private static JsonArray socketIoEvent(String packet) {
        return JsonParser.parseString(packet.substring(2)).getAsJsonArray();
    }

    private static final class PacketListener implements WebSocket.Listener {

        private final BlockingQueue<String> packets = new LinkedBlockingQueue<>();
        private final StringBuilder currentMessage = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            currentMessage.append(data);
            if (last) {
                String packet = currentMessage.toString();
                currentMessage.setLength(0);
                if ("2".equals(packet)) {
                    webSocket.sendText("3", true);
                } else {
                    packets.add(packet);
                }
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            packets.add("CLOSED:" + statusCode + ":" + reason);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            packets.add("ERROR:" + error.getMessage());
        }

        private String awaitPacket(String prefix) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline) {
                String packet = packets.poll(100, TimeUnit.MILLISECONDS);
                if (packet == null) {
                    continue;
                }
                if (packet.startsWith("ERROR:") || packet.startsWith("CLOSED:")) {
                    throw new IllegalStateException(packet);
                }
                if (packet.startsWith(prefix)) {
                    return packet;
                }
            }
            throw new IllegalStateException("Timed out waiting for Socket.IO packet " + prefix);
        }

        private String awaitEvent(String event) throws Exception {
            String prefix = "42[\"" + event + "\"";
            return awaitPacket(prefix);
        }
    }

    private static final class FixedPrincipalResolver implements SecurityPrincipalResolver {

        @Override
        public ResolvedPrincipal resolveByAuthProviderId(String authProviderId) {
            return resolved();
        }

        @Override
        public ResolvedPrincipal resolveByEmail(String email) {
            return resolved();
        }

        @Override
        public ResolvedPrincipal resolveByApiKey(String apiKey) {
            return resolved();
        }

        private ResolvedPrincipal resolved() {
            return new ResolvedPrincipal(
                    "socket-principal",
                    "socket-client",
                    "socket-customer",
                    null);
        }
    }
}
