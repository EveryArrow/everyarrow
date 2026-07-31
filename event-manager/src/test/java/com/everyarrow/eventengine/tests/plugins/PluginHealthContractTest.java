/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.tests.plugins;

import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.eventengine.core.services.plugins.LoadedPlugin;
import com.everyarrow.eventengine.core.services.plugins.PluginManager;
import com.everyarrow.eventengine.core.services.registry.ServiceRegistry;
import com.everyarrow.eventengine.core.services.runtime.ServiceCall;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.everyarrow.eventengine.spi.model.ServiceCallResponse;
import com.everyarrow.eventengine.spi.plugin.PluginHealthResult;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginHealthContractTest {

    @Test
    void pluginDirectoryWatcherIsDisabledByDefault() throws Exception {
        String previous = System.getProperty(PluginManager.PLUGIN_WATCH_ENABLED_CONFIG);
        System.clearProperty(PluginManager.PLUGIN_WATCH_ENABLED_CONFIG);
        try {
            Path pluginsRoot = Files.createTempDirectory("ea-plugin-watch-default-off");
            PluginManager pluginManager = new PluginManager(new ServiceRegistry(), pluginsRoot.toFile());

            pluginManager.startPluginWatcher(null);

            assertFalse(pluginManager.isPluginWatcherActive());
        } finally {
            restoreProperty(PluginManager.PLUGIN_WATCH_ENABLED_CONFIG, previous);
        }
    }

    @Test
    void pluginDirectoryWatcherRequiresExplicitOptIn() throws Exception {
        String previous = System.getProperty(PluginManager.PLUGIN_WATCH_ENABLED_CONFIG);
        System.setProperty(PluginManager.PLUGIN_WATCH_ENABLED_CONFIG, "true");
        Path pluginsRoot = Files.createTempDirectory("ea-plugin-watch-enabled");
        PluginManager pluginManager = new PluginManager(new ServiceRegistry(), pluginsRoot.toFile());
        try {
            pluginManager.startPluginWatcher(null);

            assertTrue(pluginManager.isPluginWatcherActive());
        } finally {
            pluginManager.stopPluginWatcher();
            restoreProperty(PluginManager.PLUGIN_WATCH_ENABLED_CONFIG, previous);
        }
    }

    @Test
    void healthyPluginReportsUpWhenRequiredServiceExists() throws Exception {
        Path pluginsRoot = Files.createTempDirectory("ea-plugin-health-up");
        Path pluginDir = pluginsRoot.resolve("healthy");
        Files.createDirectories(pluginDir);
        writeManifest(pluginDir, "healthy", List.of("test_db.public.healthy"), List.of());
        writeDbRegistry(pluginDir.resolve("service_registry.json"), "test_db.public.healthy", "/healthy");

        Engine engine = new Engine(pluginsRoot.toFile());
        engine.initialize();

        LoadedPlugin plugin = engine.getPluginManager().getLoadedPlugin("healthy").orElseThrow();
        assertEquals(PluginHealthResult.Status.UP, plugin.getHealthResult().orElseThrow().getStatus());
        assertNotNull(engine.getRegistry().getDefinition("test_db.public.healthy"));
    }

    @Test
    void missingRequiredServiceQuarantinesPluginAndRemovesDefinitions() throws Exception {
        Path pluginsRoot = Files.createTempDirectory("ea-plugin-health-missing-service");
        Path pluginDir = pluginsRoot.resolve("missing-service");
        Files.createDirectories(pluginDir);
        writeManifest(pluginDir, "missing-service", List.of("test_db.public.missing"), List.of());
        writeDbRegistry(pluginDir.resolve("service_registry.json"), "test_db.public.other", "/other");

        Engine engine = new Engine(pluginsRoot.toFile());
        engine.initialize();

        PluginManager pluginManager = engine.getPluginManager();
        assertFalse(pluginManager.getLoadedPlugin("missing-service").isPresent());
        assertTrue(pluginManager.getQuarantinedPlugin("missing-service").isPresent());
        assertEquals(PluginHealthResult.Status.DOWN,
                pluginManager.getQuarantinedPlugin("missing-service").orElseThrow()
                        .getHealthResult().orElseThrow().getStatus());
        assertNull(engine.getRegistry().getDefinition("test_db.public.other"));
    }

    @Test
    void missingRequiredConfigQuarantinesPlugin() throws Exception {
        Path pluginsRoot = Files.createTempDirectory("ea-plugin-health-missing-config");
        Path pluginDir = pluginsRoot.resolve("missing-config");
        Files.createDirectories(pluginDir);
        writeManifest(pluginDir, "missing-config", List.of("test_db.public.configured"),
                List.of("EA_TEST_REQUIRED_CONFIG_DO_NOT_SET"));
        writeDbRegistry(pluginDir.resolve("service_registry.json"), "test_db.public.configured", "/configured");

        Engine engine = new Engine(pluginsRoot.toFile());
        engine.initialize();

        assertFalse(engine.getPluginManager().getLoadedPlugin("missing-config").isPresent());
        assertTrue(engine.getPluginManager().getQuarantinedPlugin("missing-config").isPresent());
        assertNull(engine.getRegistry().getDefinition("test_db.public.configured"));
    }

    @Test
    void liveAddScanActivatesHealthyNewPlugin() throws Exception {
        Path pluginsRoot = Files.createTempDirectory("ea-plugin-live-add");
        Engine engine = new Engine(pluginsRoot.toFile());
        engine.initialize();

        Path pluginDir = pluginsRoot.resolve("live-plugin");
        Files.createDirectories(pluginDir);
        writeManifest(pluginDir, "live-plugin", List.of("test_db.public.live_plugin"), List.of());
        writeDbRegistry(pluginDir.resolve("service_registry.json"), "test_db.public.live_plugin", "/live-plugin");

        engine.getPluginManager().handlePluginRootChange("live-plugin", null);

        assertTrue(engine.getPluginManager().getLoadedPlugin("live-plugin").isPresent());
        assertNotNull(engine.getRegistry().getDefinition("test_db.public.live_plugin"));
    }

    @Test
    void loadedPluginChangeMarksRestartRequired() throws Exception {
        Path pluginsRoot = Files.createTempDirectory("ea-plugin-restart-required");
        Path pluginDir = pluginsRoot.resolve("restart-required");
        Files.createDirectories(pluginDir);
        writeManifest(pluginDir, "restart-required", List.of("test_db.public.restart_required"), List.of());
        writeDbRegistry(pluginDir.resolve("service_registry.json"),
                "test_db.public.restart_required",
                "/restart-required");

        Engine engine = new Engine(pluginsRoot.toFile());
        engine.initialize();
        engine.getPluginManager().handlePluginRootChange("restart-required", null);

        LoadedPlugin plugin = engine.getPluginManager().getLoadedPlugin("restart-required").orElseThrow();
        assertTrue(plugin.isRestartRequired());
        assertTrue(plugin.getHealthResult().orElseThrow().isRestartRequired());
    }

    @Test
    void readyRouteIsExplicitlyPublic() throws Exception {
        Path emptyPluginRoot = Files.createTempDirectory("ea-empty-ready-plugin-root");
        Engine engine = new Engine(emptyPluginRoot.toFile());
        engine.initialize();

        ServiceCallDefinition def = engine.getRegistry().getDefinition("readyCheck");
        assertNotNull(def, "Ready check definition must exist");
        assertNotNull(def.getAnnotations().secured, "Ready check should carry secured metadata");
        assertTrue(def.getAnnotations().secured.isPublic, "Ready check should be explicitly public");

        ServiceCallDefinition detailed = engine.getRegistry().getDefinition("readyDetails");
        assertNotNull(detailed, "Detailed ready definition must exist");
        assertNotNull(detailed.getAnnotations().secured, "Detailed ready route should carry secured metadata");
        assertFalse(detailed.getAnnotations().secured.isPublic, "Detailed ready route must require auth");
    }

    @Test
    void publicReadyResponseDoesNotExposePluginDetails() throws Exception {
        Path emptyPluginRoot = Files.createTempDirectory("ea-empty-public-ready-plugin-root");
        Engine engine = new Engine(emptyPluginRoot.toFile());
        engine.initialize();

        ServiceCall call = new ServiceCall();
        call.setType("readyCheck");

        ServiceCallResponse response = engine.execute(call);

        assertEquals(200, response.getHttpStatus());
        assertFalse(response.getBodyAsObject().has("plugins"));
        assertFalse(response.getBodyAsObject().has("serviceIdentity"));
        assertFalse(response.getBodyAsObject().has("pluginRoot"));
    }

    @Test
    void startupHealthSummaryPrintsObviousQuarantineAlert() throws Exception {
        Path pluginsRoot = Files.createTempDirectory("ea-plugin-health-alert");
        Path pluginDir = pluginsRoot.resolve("missing-service");
        Files.createDirectories(pluginDir);
        writeManifest(pluginDir, "missing-service", List.of("test_db.public.missing"), List.of());
        writeDbRegistry(pluginDir.resolve("service_registry.json"), "test_db.public.other", "/other");

        Engine engine = new Engine(pluginsRoot.toFile());
        engine.initialize();

        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        try {
            System.setErr(new PrintStream(errBytes, true, StandardCharsets.UTF_8));
            engine.getPluginManager().printStartupHealthSummary();
        } finally {
            System.setErr(originalErr);
        }

        String err = errBytes.toString(StandardCharsets.UTF_8);
        assertTrue(err.contains("ALERT: 1 QUARANTINED PLUGIN(S) DETECTED"));
        assertTrue(err.contains("missing-service"));
        assertTrue(err.contains("required-service:test_db.public.missing"));
    }

    private void writeManifest(Path pluginDir,
                               String pluginId,
                               List<String> requiredServices,
                               List<String> requiredConfig) throws Exception {
        Files.writeString(pluginDir.resolve("plugin.json"), """
                {
                  "plugin": {
                    "id": "%s",
                    "version": "1.0.0",
                    "health": {
                      "requiredServices": [%s],
                      "requiredConfig": [%s]
                    }
                  },
                  "services": []
                }
                """.formatted(
                pluginId,
                jsonStringList(requiredServices),
                jsonStringList(requiredConfig)),
                StandardCharsets.UTF_8);
    }

    private void writeDbRegistry(Path path, String serviceType, String routePath) throws Exception {
        Files.writeString(path, """
                [
                  {
                    "database": "test_db",
                    "schema": "public",
                    "function": "test_function",
                    "serviceType": "%s",
                    "httpMethod": "GET",
                    "routePath": "%s",
                    "description": "test",
                    "jsonType": "jsonb",
                    "annotations": {
                      "call": {
                        "applicationKey": "TESTS"
                      },
                      "secured": {
                        "roles": ["system"],
                        "scopes": ["tests:read"]
                      }
                    }
                  }
                ]
                """.formatted(serviceType, routePath), StandardCharsets.UTF_8);
    }

    private String jsonStringList(List<String> values) {
        return values.stream()
                .map(value -> "\"" + value + "\"")
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private void restoreProperty(String key, String previous) {
        if (previous == null) {
            System.clearProperty(key);
            return;
        }
        System.setProperty(key, previous);
    }
}
