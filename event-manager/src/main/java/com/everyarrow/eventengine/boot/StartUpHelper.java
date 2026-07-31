package com.everyarrow.eventengine.boot;

import com.everyarrow.utilities.environment.Environment;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Single, production-grade startup path used by both runtime and tests.
 *
 * Rules:
 * - Engine resources start ONLY from here (including embedded Artemis when configured).
 * - No test-only boot paths.
 */
public final class StartUpHelper {

    private static volatile EmbeddedArtemisTcpBroker embeddedBroker;

    private StartUpHelper() {
    }

    /** Phase 1: create + initialize (scan services, build registry). */
    public static Engine createAndInitializeEngine() throws Exception {
        loadEnvironment();
        startEmbeddedBrokerIfConfigured();
        return initializedEngine();
    }

    /** Phase 2: start adapters + Jetty. */
    public static void startEngineWithHttp(Engine engine) throws Exception {
        engine.start();
        JettyBootstrap.start(engine);
        System.out.println("[EngineBootstrap] Jetty started");
    }

    public static void installShutdownHook(Engine engine) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("JVM shutdown hook triggered");
            engine.shutdown();
        }, "ea-engine-shutdown"));
    }

    public static void waitForShutdown(Engine engine) {
        System.out.println("Engine fully started. Waiting for shutdown...");
        engine.run();
    }

    static void loadEnvironment() {
        String envResource = firstNonBlank(
                System.getProperty("EA_ENV_RESOURCE"),
                System.getenv("EA_ENV_RESOURCE"));
        String overlayResources = firstNonBlank(
                System.getProperty("EA_ENV_OVERLAY_RESOURCE"),
                System.getenv("EA_ENV_OVERLAY_RESOURCE"),
                "");
        loadEnvironment(envResource, overlayResources);
    }

    static void loadEnvironment(String envResource, String overlayResources) {
        System.out.println("[EngineBootstrap] Loading environment");

        if (envResource.isBlank()) {
            Environment.reset();
            System.out.println("[EngineBootstrap] No env file configured; using process environment");
        }
        loadEnvironmentSource(envResource, true);

        overlayResources = firstNonBlank(overlayResources, "").trim();
        if (overlayResources.isEmpty()) {
            return;
        }

        for (String overlayResource : overlayResources.split(",")) {
            String trimmedOverlay = overlayResource.trim();
            if (!trimmedOverlay.isEmpty()) {
                loadEnvironmentSource(trimmedOverlay, false);
            }
        }
    }

    private static void startEmbeddedBrokerIfConfigured() throws Exception {
        Environment env = Environment.getInstance();
        String brokerUrl = env.get("JMS_BROKER_URL", "").trim();
        String brokerUsername = env.get("JMS_USERNAME", "").trim();
        String brokerPassword = env.get("JMS_PASSWORD", "").trim();
        boolean allowPublicEmbeddedBroker = Boolean.parseBoolean(
                env.get("EA_EMBEDDED_ARTEMIS_ALLOW_PUBLIC_BIND", "false"));

        System.out.println("[EngineBootstrap] Artemis brokerUrl=" + brokerUrl);

        if (embeddedBroker != null) {
            return;
        }

        embeddedBroker = EmbeddedArtemisTcpBroker.startIfLocalTcp(
                brokerUrl,
                brokerUsername,
                brokerPassword,
                allowPublicEmbeddedBroker);
        if (embeddedBroker != null) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    embeddedBroker.close();
                } catch (Exception ignored) {
                }
            }, "ea-embedded-artemis-shutdown"));
        }
    }

    private static Engine initializedEngine() throws Exception {
        System.out.println("[EngineBootstrap] Creating engine");
        Engine engine = new Engine();
        engine.initialize();
        return engine;
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

    private static void loadEnvironmentSource(String source, boolean reset) {
        if (source == null || source.isBlank()) {
            return;
        }

        Path filePath = Path.of(source.trim());
        if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
            if (reset) {
                Environment.initFromFile(filePath);
            } else {
                System.out.println("[EngineBootstrap] Skipping file overlay source '" + source
                        + "' because additive file overlays are not supported here; relying on process environment values instead.");
            }
            return;
        }

        if (reset) {
            Environment.initFromResource(source.trim());
        } else {
            Environment.loadComponent(source.trim());
        }
    }
}
