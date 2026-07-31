package com.everyarrow.eventengine.tests;

import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.eventengine.boot.JettyBootstrap;
import com.everyarrow.eventengine.boot.StartUpHelper;
import com.everyarrow.eventengine.core.services.plugins.PluginRootResolver;
import com.everyarrow.eventengine.core.services.runtime.EventManagerServiceIdentity;
import com.everyarrow.utilities.environment.Environment;

import java.util.LinkedHashMap;
import java.util.Map;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;

public final class EngineHarness implements AutoCloseable {

    private static final String TEST_BROKER_URL = "tcp://127.0.0.1:" + availableTcpPort();

    private final Engine engine;
    private final int httpPort;
    private final String previousEnvResource;
    private final String previousPluginRoot;
    private final Map<String, String> previousEnvironmentValues;

    private EngineHarness(
            Engine engine,
            int httpPort,
            String previousEnvResource,
            String previousPluginRoot,
            Map<String, String> previousEnvironmentValues) {
        this.engine = engine;
        this.httpPort = httpPort;
        this.previousEnvResource = previousEnvResource;
        this.previousPluginRoot = previousPluginRoot;
        this.previousEnvironmentValues = previousEnvironmentValues;
    }

    public static EngineHarness start() throws Exception {

        // Avoid fixed-port conflicts (especially in IDE / repeated runs).
        // JettyBootstrap already reads EVENT_ENGINE_HTTP_PORT, so this is a normal config override
        // (not a test-only startup path or "BootMode").
        if (System.getProperty("EVENT_ENGINE_HTTP_PORT") == null) {
            System.setProperty("EVENT_ENGINE_HTTP_PORT", "0"); // ephemeral port
        }
        if (System.getProperty(EventManagerServiceIdentity.API_KEY_CONFIG) == null) {
            System.setProperty(EventManagerServiceIdentity.API_KEY_CONFIG, "test-service-worker-key");
        }
        String previousEnvResource = System.getProperty("EA_ENV_RESOURCE");
        if (previousEnvResource == null || previousEnvResource.isBlank()) {
            System.setProperty("EA_ENV_RESOURCE", "ea.test.env");
        }
        Map<String, String> previousEnvironmentValues = applyHarnessEnvironment();
        String previousPluginRoot = System.getProperty(PluginRootResolver.CONFIG_KEY);
        if (previousPluginRoot == null || previousPluginRoot.isBlank()) {
            Path isolatedPluginRoot = Files.createTempDirectory("ea-engine-harness-plugins");
            System.setProperty(PluginRootResolver.CONFIG_KEY, isolatedPluginRoot.toString());
        }

        // Phase 1: production init path
        Engine engine = StartUpHelper.createAndInitializeEngine();

        // Test-only: register event types that live in src/test/java
        TestEventTypeRegistrar.registerAll(engine);

        // Phase 2: production start + Jetty
        StartUpHelper.startEngineWithHttp(engine);

        int port = JettyBootstrap.getPort();

        System.out.println("[EngineHarness] Engine + HTTP started for tests on port=" + port);
        return new EngineHarness(engine, port, previousEnvResource, previousPluginRoot, previousEnvironmentValues);
    }

    // ✅ Correct type: return Engine, not EngineHarness
    public Engine getEngine() {
        return engine;
    }

    public int getHttpPort() {
        return httpPort;
    }

    public String getBaseUrl() {
        return "http://localhost:" + httpPort;
    }

    @Override
    public void close() {
        System.out.println("[EngineHarness] Shutting down engine (tests)...");
        engine.shutdown();
        if (previousEnvResource == null) {
            System.clearProperty("EA_ENV_RESOURCE");
        } else {
            System.setProperty("EA_ENV_RESOURCE", previousEnvResource);
        }
        if (previousPluginRoot == null) {
            System.clearProperty(PluginRootResolver.CONFIG_KEY);
        } else {
            System.setProperty(PluginRootResolver.CONFIG_KEY, previousPluginRoot);
        }
        previousEnvironmentValues.forEach(Environment::put);
    }

    private static Map<String, String> applyHarnessEnvironment() {
        Map<String, String> previousValues = new LinkedHashMap<>();

        putHarnessValue(previousValues, "JMS_BROKER_URL", TEST_BROKER_URL);
        putHarnessValue(previousValues, "JMS_USERNAME", "");
        putHarnessValue(previousValues, "JMS_PASSWORD", "");
        putHarnessValue(previousValues, "EA_EMBEDDED_ARTEMIS_ALLOW_PUBLIC_BIND", "false");
        putHarnessValue(previousValues, EventManagerServiceIdentity.API_KEY_CONFIG, "test-service-worker-key");
        putHarnessValue(previousValues, "EA_EVENT_MANAGER_SERVICE_PRINCIPAL_ID", "00000000-0000-0000-0000-000000000010");
        putHarnessValue(previousValues, "EA_EVENT_MANAGER_SERVICE_CUSTOMER_ID", "00000000-0000-0000-0000-000000000011");
        putHarnessValue(previousValues, "EVENT_MANAGER_CONSOLE_ID", "00000000-0000-0000-0000-000000000001");
        putHarnessValue(previousValues, "TESTS_CONSOLE_ID", "00000000-0000-0000-0000-000000000002");
        putHarnessValue(previousValues, "EA_SECURITY_PUBLIC_SERVICE_TYPES",
                "calendarJobTestService,queueHelloWorldService,webSocketEcho,restTest,demoWebhookCsv");

        return previousValues;
    }

    private static void putHarnessValue(Map<String, String> previousValues, String key, String value) {
        previousValues.put(key, Environment.getInstance().get(key, ""));
        Environment.put(key, value);
    }

    private static int availableTcpPort() {
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
