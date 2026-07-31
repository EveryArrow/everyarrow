package com.everyarrow.eventengine.boot;

import com.everyarrow.eventengine.core.adapters.external.http.RoutedRestServlet;
import com.everyarrow.eventengine.core.adapters.external.http.HttpSecurityFilter;
import com.everyarrow.eventengine.core.adapters.external.http.webhook.WebhookServlet;
import com.everyarrow.utilities.environment.Environment;
import jakarta.servlet.DispatcherType;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.ee10.websocket.server.config.JettyWebSocketServletContainerInitializer;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class JettyBootstrap {

    private static Server server;
    private static volatile int boundPort = -1;
    private static volatile String boundHost = "";
    private static final List<QueuedServlet> queuedServlets = new CopyOnWriteArrayList<>();

    private JettyBootstrap() {
    }

    public static void start(Engine engine) {
        try {
            if (reuseRunningServer(engine)) {
                return;
            }

            server = configuredServer();
            server.setHandler(servletContext(engine));
            server.start();

            rememberBinding(server);
            System.out.println("Jetty HTTP/Socket.IO started on " + boundHost + ":" + boundPort);
        } catch (Exception e) {
            throw new RuntimeException("Jetty failed to start", e);
        }
    }

    public static void stop() {
        if (server == null) {
            return;
        }

        try {
            server.stop();
            server = null;
            boundPort = -1;
            boundHost = "";
        } catch (Exception e) {
            throw new RuntimeException("Jetty failed to stop", e);
        }
    }

    public static void queueServlet(ServletHolder holder, String pathSpec) {
        if (holder == null) {
            throw new IllegalArgumentException("ServletHolder is required");
        }
        if (pathSpec == null || pathSpec.isBlank()) {
            throw new IllegalArgumentException("Servlet path is required");
        }
        queuedServlets.add(new QueuedServlet(holder, pathSpec));
    }

    /**
     * Returns the actual bound port (important when EVENT_ENGINE_HTTP_PORT=0).
     */
    public static int getPort() {
        if (boundPort > 0) {
            return boundPort;
        }
        if (server != null) {
            boundPort = resolveLocalPort(server);
        }
        return boundPort;
    }

    public static String getHost() {
        if (boundHost != null && !boundHost.isBlank()) {
            return boundHost;
        }
        if (server != null) {
            boundHost = resolveLocalHost(server);
        }
        return boundHost;
    }

    private static boolean reuseRunningServer(Engine engine) {
        if (server == null || !server.isRunning()) {
            return false;
        }

        bindServletsTo(engine);
        rememberBinding(server);
        return true;
    }

    private static Server configuredServer() {
        Environment environment = Environment.getInstance();
        int requestedPort = Integer.parseInt(firstNonBlank(
                System.getProperty("EVENT_ENGINE_HTTP_PORT"),
                environment.get("EVENT_ENGINE_HTTP_PORT", ""),
                "8080"));
        String requestedHost = firstNonBlank(
                System.getProperty("EVENT_ENGINE_HTTP_HOST"),
                environment.get("EVENT_ENGINE_HTTP_HOST", ""),
                "127.0.0.1").trim();

        Server configuredServer = new Server();
        ServerConnector connector = new ServerConnector(configuredServer);
        connector.setHost(requestedHost.isEmpty() ? "127.0.0.1" : requestedHost);
        connector.setPort(requestedPort);
        configuredServer.addConnector(connector);
        return configuredServer;
    }

    private static ServletContextHandler servletContext(Engine engine) {
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.setContextPath("/");
        JettyWebSocketServletContainerInitializer.configure(context, (servletContext, container) -> {
            // SocketIoGatewayServlet configures its Engine.IO websocket creator.
        });
        context.addFilter(
                new FilterHolder(new HttpSecurityFilter()),
                "/*",
                EnumSet.of(DispatcherType.REQUEST));

        bindServletsTo(engine);
        for (QueuedServlet queued : queuedServlets) {
            context.addServlet(queued.holder(), queued.pathSpec());
        }
        context.addServlet(new ServletHolder(engine.getRealtimeGateway()), "/socket.io/*");
        context.addServlet(new ServletHolder(new WebhookServlet()), "/webhooks/*");
        context.addServlet(new ServletHolder(new RoutedRestServlet()), "/*");
        return context;
    }

    private static void bindServletsTo(Engine engine) {
        RoutedRestServlet.setEngine(engine);
        WebhookServlet.setEngine(engine);
    }

    private static void rememberBinding(Server runningServer) {
        boundPort = resolveLocalPort(runningServer);
        boundHost = resolveLocalHost(runningServer);
    }

    private static int resolveLocalPort(Server runningServer) {
        if (runningServer == null) {
            return -1;
        }
        try {
            if (runningServer.getConnectors() == null || runningServer.getConnectors().length == 0) {
                return -1;
            }
            if (runningServer.getConnectors()[0] instanceof ServerConnector connector) {
                return connector.getLocalPort();
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    private static String resolveLocalHost(Server runningServer) {
        if (runningServer == null) {
            return "";
        }
        try {
            if (runningServer.getConnectors() == null || runningServer.getConnectors().length == 0) {
                return "";
            }
            if (runningServer.getConnectors()[0] instanceof ServerConnector connector) {
                String host = connector.getHost();
                return host == null || host.isBlank() ? "0.0.0.0" : host;
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private record QueuedServlet(ServletHolder holder, String pathSpec) {
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
