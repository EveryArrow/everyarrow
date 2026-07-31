/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.tests.plugins;

import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.eventengine.core.services.plugins.PluginManager;
import com.everyarrow.eventengine.core.services.runtime.GenericProcessHandler;
import com.everyarrow.eventengine.core.services.runtime.PostgresHelper;
import com.everyarrow.eventengine.spi.handler.ServiceCallHandler;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PluginManifestGenericFunctionRegistrationTest {

    private static final String SAMPLE_REST_HANDLER =
            "com.everyarrow.eventengine.plugins.sample.TestRestHandler";
    private static final String SERVICE_TYPE = "test_db.public.hello_world";
    private static final String SOURCE = "db:test_db";
    private static final String JSON_TYPE = "jsonb";

    @Test
    void manifestOnlyPluginShouldRegisterAndExecuteGenericFunction() throws Exception {
        Path pluginsRoot = Files.createTempDirectory("ea-plugin-manifest-only");
        writePluginManifest(
                pluginsRoot.resolve("manifestOnly"),
                "manifest-only",
                SERVICE_TYPE,
                SOURCE
        );

        Engine engine = new Engine();
        PluginManager pluginManager = new PluginManager(engine.getRegistry(), pluginsRoot.toFile());
        pluginManager.setEngine(engine);
        pluginManager.loadPlugins();

        assertTrue(pluginManager.getLoadedPlugin("manifestOnly").isPresent(),
                "Expected manifest-only plugin directory to load");

        ServiceCallDefinition def = engine.getRegistry().getDefinition(SERVICE_TYPE);
        assertNotNull(def, "Expected manifest-defined generic function to be registered");
        assertTrue(def.isActive(), "Expected manifest definition to be active");
        assertEquals(SOURCE, def.getSource());
        assertEquals("GenericProcessHandler", def.getHandlerClass());
        assertEquals("manifest-only",
                engine.getRegistry()
                        .getResolution(SERVICE_TYPE)
                        .selected()
                        .contract()
                        .pluginId());
        assertEquals("1.0.0",
                engine.getRegistry()
                        .getResolution(SERVICE_TYPE)
                        .selected()
                        .pluginVersion());

        ServiceCallHandler handler = engine.getRegistry().get(SERVICE_TYPE);
        assertNotNull(handler, "Expected GenericProcessHandler to be bound at runtime");
        assertInstanceOf(GenericProcessHandler.class, handler);

        StubPostgresHelper pg = new StubPostgresHelper();
        replaceRegisteredHandler(engine, SERVICE_TYPE, def, handler, new GenericProcessHandler(engine, pg));

        JsonObject params = new JsonObject();
        params.addProperty("name", "Manifest");

        JsonObject result = engine.getRegistry().get(SERVICE_TYPE).execute(params, SERVICE_TYPE, "registry").getAsJsonObject();
        assertEquals("Hello World", result.get("message").getAsString());
        assertEquals("test_db", pg.database);
        assertEquals("public", pg.schema);
        assertEquals("hello_world", pg.function);
        assertEquals(JSON_TYPE, pg.jsonType);
        assertEquals("Manifest", pg.params.get("name").getAsString());
    }

    @Test
    void genericFunctionShouldPreferRegistryDatabaseSourceForNamespacedServiceType() throws Exception {
        String serviceType = "security.access_control.health_check";
        Engine engine = new Engine();
        engine.getRegistry().deregisterServiceType(serviceType);

        ServiceCallDefinition def = ServiceCallDefinition.fromDb(
                serviceType,
                "EveryArrow",
                "app_security",
                "health_check",
                null,
                null,
                "App security health check",
                JSON_TYPE
        );
        assertEquals("db:EveryArrow", def.getSource());

        StubPostgresHelper pg = new StubPostgresHelper();
        engine.getRegistry().register(serviceType, new GenericProcessHandler(engine, pg), def);

        engine.getRegistry().get(serviceType).execute(new JsonObject(), serviceType, "registry");

        assertEquals("EveryArrow", pg.database);
        assertEquals("app_security", pg.schema);
        assertEquals("health_check", pg.function);
    }

    @Test
    void mixedPluginDirectoryShouldLoadManifestGenericFunctionsAndAnnotatedHandlers() throws Exception {
        Path sampleJar = SamplePluginJarFixture.build();

        Path pluginsRoot = Files.createTempDirectory("ea-plugin-mixed");
        Path pluginDir = pluginsRoot.resolve("mixed");
        Path nestedLibDir = pluginDir.resolve("lib");
        Files.createDirectories(nestedLibDir);
        Files.copy(sampleJar, nestedLibDir.resolve(sampleJar.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        writePluginManifest(pluginDir, "mixed-plugin", SERVICE_TYPE, SOURCE);

        Engine engine = new Engine();
        PluginManager pluginManager = new PluginManager(engine.getRegistry(), pluginsRoot.toFile());
        pluginManager.setEngine(engine);
        pluginManager.loadPlugins();

        assertTrue(pluginManager.findClass(SAMPLE_REST_HANDLER).isPresent(),
                "Expected annotated plugin handler to be discovered from plugin JAR");

        ServiceCallDefinition genericDef = engine.getRegistry().getDefinition(SERVICE_TYPE);
        assertNotNull(genericDef, "Expected manifest-defined generic function to be registered");
        assertNotNull(engine.getRegistry().getDefinition("testRest"),
                "Expected annotated handler definition to still be registered");

        ServiceCallHandler manifestHandler = engine.getRegistry().get(SERVICE_TYPE);
        StubPostgresHelper pg = new StubPostgresHelper();
        replaceRegisteredHandler(engine, SERVICE_TYPE, genericDef, manifestHandler, new GenericProcessHandler(engine, pg));

        JsonObject result = engine.getRegistry().get(SERVICE_TYPE).execute(new JsonObject(), SERVICE_TYPE, "registry").getAsJsonObject();
        assertEquals("Hello World", result.get("message").getAsString());
        assertEquals("test_db", pg.database);
    }

    @Test
    void mixedPluginDirectoryShouldStillDiscoverNestedJarRecursively() throws Exception {
        Path sampleJar = SamplePluginJarFixture.build();

        Path pluginsRoot = Files.createTempDirectory("ea-plugin-recursive-mixed");
        Path pluginDir = pluginsRoot.resolve("recursive");
        Path nestedLibDir = pluginDir.resolve("lib").resolve("nested");
        Files.createDirectories(nestedLibDir);
        Files.copy(sampleJar, nestedLibDir.resolve(sampleJar.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        writePluginManifest(pluginDir, "recursive-plugin", SERVICE_TYPE, SOURCE);

        Engine engine = new Engine();
        PluginManager pluginManager = new PluginManager(engine.getRegistry(), pluginsRoot.toFile());
        pluginManager.setEngine(engine);
        pluginManager.loadPlugins();

        assertTrue(pluginManager.findClass(SAMPLE_REST_HANDLER).isPresent(),
                "Expected recursive nested JAR discovery to keep working");
        assertNotNull(engine.getRegistry().getDefinition(SERVICE_TYPE),
                "Expected manifest generic function to be registered from recursive plugin directory");
    }

    @Test
    void namespacedServiceTypeShouldPreferRegistryDatabaseSource() throws Exception {
        String namespacedServiceType = "perch_dev.perch.get_group_by_name";
        Engine engine = new Engine(Files.createTempDirectory("ea-empty-plugin-root").toFile());
        ServiceCallDefinition def = new ServiceCallDefinition();
        def.setServiceType(namespacedServiceType);
        def.setSource("db:EveryArrow");
        def.setHandlerClass("GenericProcessHandler");
        def.setJsonType(JSON_TYPE);

        StubPostgresHelper pg = new StubPostgresHelper();
        engine.getRegistry().register(namespacedServiceType, new GenericProcessHandler(engine, pg), def);

        JsonObject result = engine.getRegistry()
                .get(namespacedServiceType)
                .execute(new JsonObject(), namespacedServiceType, "registry")
                .getAsJsonObject();

        assertEquals("Hello World", result.get("message").getAsString());
        assertEquals("EveryArrow", pg.database);
        assertEquals("perch", pg.schema);
        assertEquals("get_group_by_name", pg.function);
        assertEquals(JSON_TYPE, pg.jsonType);
    }

    @Test
    void explicitOverridePluginShouldResolveAndFallbackInBothInstallOrders()
            throws Exception {
        Path sampleJar = SamplePluginJarFixture.build();

        for (boolean overrideFirst : List.of(false, true)) {
            Path pluginsRoot = Files.createTempDirectory(
                    "ea-plugin-explicit-override");
            Path generatedDir = pluginsRoot.resolve(
                    overrideFirst ? "z-generated" : "a-generated");
            Path overrideDir = pluginsRoot.resolve(
                    overrideFirst ? "a-override" : "z-override");

            writePluginManifest(
                    generatedDir,
                    "generated-security",
                    SERVICE_TYPE,
                    SOURCE);
            writeOverrideManifest(
                    overrideDir,
                    "custom-security",
                    SERVICE_TYPE);
            Path overrideLib = overrideDir.resolve("lib");
            Files.createDirectories(overrideLib);
            Files.copy(
                    sampleJar,
                    overrideLib.resolve(sampleJar.getFileName()),
                    StandardCopyOption.REPLACE_EXISTING);

            Engine engine = new Engine();
            PluginManager pluginManager = new PluginManager(
                    engine.getRegistry(),
                    pluginsRoot.toFile());
            pluginManager.setEngine(engine);
            pluginManager.loadPlugins();
            engine.getRegistry().validateExplicitOverrides();

            assertEquals(SAMPLE_REST_HANDLER,
                    engine.getRegistry()
                            .getDefinition(SERVICE_TYPE)
                            .getHandlerClass());
            assertEquals("custom-security",
                    engine.getRegistry()
                            .getResolution(SERVICE_TYPE)
                            .selected()
                            .pluginId());
            assertEquals("valid",
                    engine.getRegistry()
                            .getResolution(SERVICE_TYPE)
                            .overrideEvidence()
                            .status()
                            .name()
                            .toLowerCase());

            engine.getRegistry().deregisterPlugin("custom-security");

            assertEquals("generated-security",
                    engine.getRegistry()
                            .getResolution(SERVICE_TYPE)
                            .selected()
                            .pluginId());
            assertInstanceOf(
                    GenericProcessHandler.class,
                    engine.getRegistry().get(SERVICE_TYPE));
        }
    }

    private void writePluginManifest(Path pluginDir, String pluginId, String serviceType, String source) throws Exception {
        Path manifestPath = pluginDir.resolve("META-INF").resolve("everyarrow").resolve("plugin.json");
        Files.createDirectories(manifestPath.getParent());
        Files.writeString(manifestPath, """
                {
                  "plugin": {
                    "id": "%s",
                    "version": "1.0.0"
                  },
                  "services": [
                    {
                      "serviceType": "%s",
                      "source": "%s",
                      "category": "Database",
                      "handlerClass": "GenericProcessHandler",
                      "jsonType": "%s",
                      "annotations": {
                        "functionContract": {
                          "namespace": "test_db.public",
                          "version": "1.0.0",
                          "signature": "hello-world-v1",
                          "capabilities": ["service_call", "jsonb"]
                        }
                      }
                    }
                  ]
                }
                """.formatted(pluginId, serviceType, source, JSON_TYPE), StandardCharsets.UTF_8);
    }

    private void writeOverrideManifest(
            Path pluginDir,
            String pluginId,
            String serviceType) throws Exception {
        Path manifestPath = pluginDir.resolve("plugin.json");
        Files.createDirectories(manifestPath.getParent());
        Files.writeString(manifestPath, """
                {
                  "plugin": {
                    "id": "%s",
                    "version": "2.0.0"
                  },
                  "services": [
                    {
                      "serviceType": "%s",
                      "handlerClass": "%s",
                      "jsonType": "%s",
                      "annotations": {
                        "functionContract": {
                          "namespace": "test_db.public",
                          "version": "1.0.0",
                          "signature": "hello-world-v1",
                          "capabilities": ["service_call", "jsonb", "audit"]
                        },
                        "functionOverride": {
                          "targetServiceType": "%s",
                          "targetPluginId": "generated-security",
                          "versionRange": "[1.0.0,2.0.0)"
                        }
                      }
                    }
                  ]
                }
                """.formatted(
                pluginId,
                serviceType,
                SAMPLE_REST_HANDLER,
                JSON_TYPE,
                serviceType), StandardCharsets.UTF_8);
    }

    private void replaceRegisteredHandler(
            Engine engine,
            String serviceType,
            ServiceCallDefinition def,
            ServiceCallHandler existingHandler,
            ServiceCallHandler replacementHandler) {
        engine.getRegistry().removeHandler(serviceType, existingHandler);
        engine.getRegistry().register(serviceType, replacementHandler, def);
    }

    private static class StubPostgresHelper extends PostgresHelper {
        private String database;
        private String schema;
        private String function;
        private String jsonType;
        private JsonObject params;

        @Override
        public JsonObject processDbFunction(String dbName,
                                            String schema,
                                            String function,
                                            String jsonType,
                                            JsonObject params) {
            this.database = dbName;
            this.schema = schema;
            this.function = function;
            this.jsonType = jsonType;
            this.params = params;

            JsonObject result = new JsonObject();
            result.addProperty("message", "Hello World");
            return result;
        }
    }
}
