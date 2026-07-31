/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.tests.plugins;

import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.eventengine.core.services.plugins.EnginePluginContextImpl;
import com.everyarrow.eventengine.core.services.plugins.LoadedPlugin;
import com.everyarrow.eventengine.core.services.plugins.PluginManager;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import org.junit.jupiter.api.Test;

import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginRegistryLoadingTest {

    @Test
    void registryOnlyPluginDirectoryShouldLoadRootRegistryFilesWithoutManifest() throws Exception {
        Path pluginsRoot = Files.createTempDirectory("ea-plugin-registry-only");
        Path pluginDir = pluginsRoot.resolve("registry-only");
        Files.createDirectories(pluginDir);

        writeDbRegistry(pluginDir.resolve("service_registry_alpha.json"),
                "test_db.public.registry_only_alpha",
                "test_db",
                "alpha",
                "GET",
                "/registry-only/alpha");
        writeDbRegistry(pluginDir.resolve("service_registry_beta.json"),
                "test_db.public.registry_only_beta",
                "test_db",
                "beta",
                "POST",
                "/registry-only/beta");

        Engine engine = new Engine();
        PluginManager pluginManager = new PluginManager(engine.getRegistry(), pluginsRoot.toFile());
        pluginManager.setEngine(engine);

        pluginManager.loadPlugins();

        assertTrue(pluginManager.getLoadedPlugin("registry-only").isPresent());
        assertNotNull(engine.getRegistry().getDefinition("test_db.public.registry_only_alpha"));
        assertNotNull(engine.getRegistry().getDefinition("test_db.public.registry_only_beta"));
    }

    @Test
    void previousPluginDirectoryShouldBeIgnored() throws Exception {
        Path pluginsRoot = Files.createTempDirectory("ea-plugin-previous-ignored");
        Path previousPluginDir = pluginsRoot.resolve("previous").resolve("old-plugin");
        Files.createDirectories(previousPluginDir);

        writeDbRegistry(previousPluginDir.resolve("service_registry_old.json"),
                "test_db.public.old_plugin_alpha",
                "test_db",
                "old_plugin_alpha",
                "GET",
                "/old-plugin/alpha");

        Engine engine = new Engine();
        PluginManager pluginManager = new PluginManager(engine.getRegistry(), pluginsRoot.toFile());
        pluginManager.setEngine(engine);

        pluginManager.loadPlugins();

        assertFalse(pluginManager.getLoadedPlugin("old-plugin").isPresent());
        assertFalse(pluginManager.getDefinition("test_db.public.old_plugin_alpha").isPresent());
    }

    @Test
    void collapsedMarketingPluginDirectoryShouldLoadAllFourFolderNamedRegistryFiles() throws Exception {
        Path pluginsRoot = Files.createTempDirectory("ea-plugin-collapsed-marketing");
        Path pluginDir = pluginsRoot.resolve("marketing");
        Files.createDirectories(pluginDir);

        writeSchemaRegistry(
                pluginDir.resolve("marketing-contact.json"),
                "marketing",
                "contact",
                "marketing",
                "contact",
                "marketing.contact.crm_dashboard_stats",
                "crm_dashboard_stats",
                "GET",
                "/marketing/crm/dashboard");
        writeSchemaRegistry(
                pluginDir.resolve("marketing-demo.json"),
                "marketing",
                "demo",
                "marketing",
                "demo",
                "marketing.demo.book_demo",
                "book_demo",
                "POST",
                "/marketing/demo/book");
        writeSchemaRegistry(
                pluginDir.resolve("marketing-public.json"),
                "marketing",
                "public",
                "marketing",
                "public",
                "marketing.public.update_service_call_type",
                "update_service_call_type",
                "POST",
                "/marketing/admin/service-call-types");
        writeSchemaRegistry(
                pluginDir.resolve("marketing-spider.json"),
                "marketing",
                "spider",
                "marketing",
                "spider",
                "marketing.spider.crm_list_searches",
                "crm_list_searches",
                "GET",
                "/marketing/crm/searches");

        Engine engine = new Engine();
        PluginManager pluginManager = new PluginManager(engine.getRegistry(), pluginsRoot.toFile());
        pluginManager.setEngine(engine);
        pluginManager.loadPlugins();

        LoadedPlugin loadedPlugin = pluginManager.getLoadedPlugin("marketing").orElseThrow();
        assertEquals(4, loadedPlugin.getServiceRegistrySources().size());
        assertNotNull(engine.getRegistry().getDefinition("marketing.contact.crm_dashboard_stats"));
        assertNotNull(engine.getRegistry().getDefinition("marketing.demo.book_demo"));
        assertNotNull(engine.getRegistry().getDefinition("marketing.public.update_service_call_type"));
        assertNotNull(engine.getRegistry().getDefinition("marketing.spider.crm_list_searches"));
    }

    @Test
    void collapsedEveryArrowConsolePluginDirectoryShouldLoadConsoleAndPipelineRegistryFiles() throws Exception {
        Path pluginsRoot = Files.createTempDirectory("ea-plugin-collapsed-console");
        Path pluginDir = pluginsRoot.resolve("everyarrow-console");
        Files.createDirectories(pluginDir);

        writeSchemaRegistry(
                pluginDir.resolve("everyarrow-console.json"),
                "EveryArrow",
                "console",
                "EveryArrow",
                "console",
                "everyarrow.console.get_console",
                "get_console",
                "GET",
                "/console");
        writeSchemaRegistry(
                pluginDir.resolve("everyarrow-pipeline.json"),
                "EveryArrow",
                "pipeline",
                "EveryArrow",
                "pipeline",
                "everyarrow.pipeline.get_pipeline_metadata_group",
                "get_pipeline_metadata_group",
                "GET",
                "/pipeline-metadata-group");

        Engine engine = new Engine();
        PluginManager pluginManager = new PluginManager(engine.getRegistry(), pluginsRoot.toFile());
        pluginManager.setEngine(engine);
        pluginManager.loadPlugins();

        LoadedPlugin loadedPlugin = pluginManager.getLoadedPlugin("everyarrow-console").orElseThrow();
        assertEquals(2, loadedPlugin.getServiceRegistrySources().size());
        assertNotNull(engine.getRegistry().getDefinition("everyarrow.console.get_console"));
        assertNotNull(engine.getRegistry().getDefinition("everyarrow.pipeline.get_pipeline_metadata_group"));
    }

    @Test
    void pluginRegistryShouldLoadServiceCallOnlyDbFunctions() throws Exception {
        Path pluginsRoot = Files.createTempDirectory("ea-plugin-service-call-only-db");
        Path pluginDir = pluginsRoot.resolve("service-call-only-db-plugin");
        String resourcePath = "META-INF/everyarrow/service-call-only/EveryArrow.app_security.service_registry.json";
        writeRootPluginManifest(pluginDir, "service-call-only-db-plugin", List.of(resourcePath));
        writeServiceCallOnlyDbRegistry(
                pluginDir.resolve(resourcePath),
                "EveryArrow",
                "app_security",
                "principal_select_one");

        Engine engine = new Engine();
        PluginManager pluginManager = new PluginManager(engine.getRegistry(), pluginsRoot.toFile());
        pluginManager.setEngine(engine);
        pluginManager.loadPlugins();

        assertTrue(pluginManager.getLoadedPlugin("service-call-only-db-plugin").isPresent());
        ServiceCallDefinition def = engine.getRegistry().getDefinition("EveryArrow.app_security.principal_select_one");
        assertNotNull(def);
        assertEquals("db:EveryArrow", def.getSource());
        assertEquals("app_security", def.getDbSchema());
        assertEquals("principal_select_one", def.getDbFunction());
        assertFalse(def.hasRestRoute());
        assertEquals(null, engine.getRegistry().findRestRoute("GET", "/app-security/principal-select-one"));
    }

    @Test
    void unpackedPluginShouldLoadRootPluginJsonAndMultipleRegistryFiles() throws Exception {
        Path pluginsRoot = Files.createTempDirectory("ea-plugin-unpacked-root");
        Path pluginDir = pluginsRoot.resolve("unpacked-root");
        Files.createDirectories(pluginDir);

        writeRootPluginManifest(pluginDir, "unpacked-plugin", List.of());
        writeDbRegistry(pluginDir.resolve("service_registry_alpha.json"),
                "test_db.public.unpacked_alpha",
                "test_db",
                "unpacked_alpha",
                "GET",
                "/unpacked/alpha");
        writeDbRegistry(pluginDir.resolve("service_registry_beta.json"),
                "test_db.public.unpacked_beta",
                "test_db",
                "unpacked_beta",
                "POST",
                "/unpacked/beta");

        Engine engine = new Engine();
        PluginManager pluginManager = new PluginManager(engine.getRegistry(), pluginsRoot.toFile());
        pluginManager.setEngine(engine);
        pluginManager.loadPlugins();

        LoadedPlugin loadedPlugin = pluginManager.getLoadedPlugin("unpacked-root").orElseThrow();
        assertTrue(loadedPlugin.getRegistryDocument().isPresent());
        assertEquals(2, loadedPlugin.getServiceRegistrySources().size());

        ServiceCallDefinition alpha = engine.getRegistry().getDefinition("test_db.public.unpacked_alpha");
        ServiceCallDefinition beta = engine.getRegistry().getDefinition("test_db.public.unpacked_beta");

        assertNotNull(alpha);
        assertNotNull(beta);
        assertEquals("/unpacked/alpha", alpha.getRestRoute().path);
        assertEquals("/unpacked/beta", beta.getRestRoute().path);
    }

    @Test
    void schemaPluginShouldLoadSchemaRegistryWithoutManifest() throws Exception {
        Path pluginsRoot = Files.createTempDirectory("ea-plugin-schema-snake-case");
        Path pluginDir = pluginsRoot.resolve("perch-extension");
        Files.createDirectories(pluginDir);

        writeSchemaRegistry(
                pluginDir.resolve("schema_registry.json"),
                "EveryArrow",
                "telemetry",
                "EveryArrow",
                "telemetry",
                "perch.telemetry.snake_case_function",
                "snake_case_function",
                "POST",
                "/snake-case-function");

        Engine engine = new Engine();
        PluginManager pluginManager = new PluginManager(engine.getRegistry(), pluginsRoot.toFile());
        pluginManager.setEngine(engine);
        pluginManager.loadPlugins();

        assertTrue(pluginManager.getLoadedPlugin("perch-extension").isPresent());
        ServiceCallDefinition def = engine.getRegistry().getDefinition("perch.telemetry.snake_case_function");
        assertNotNull(def);
        assertEquals("db:EveryArrow", def.getSource());
        assertEquals("/snake-case-function", def.getRestRoute().path);
        assertEquals(1, pluginManager.getExternalRegistryDefinitionCount());
    }

    @Test
    void schemaPluginShouldRejectOutOfScopeRegistryFile() throws Exception {
        Path pluginsRoot = Files.createTempDirectory("ea-plugin-schema-scope");
        Path pluginDir = pluginsRoot.resolve("security-extension");
        Files.createDirectories(pluginDir);

        writeSchemaRegistry(
                pluginDir.resolve("schema_registry.json"),
                "EveryArrow",
                "app_security",
                "EveryArrow",
                "telemetry",
                "perch.telemetry.wrong_schema",
                "wrong_schema",
                "POST",
                "/wrong-schema");

        Engine engine = new Engine();
        PluginManager pluginManager = new PluginManager(engine.getRegistry(), pluginsRoot.toFile());
        pluginManager.setEngine(engine);

        IllegalStateException error = assertThrows(IllegalStateException.class, pluginManager::loadPlugins);
        assertTrue(error.getMessage().contains("scoped to EveryArrow.app_security"));
        assertTrue(error.getMessage().contains("perch.telemetry.wrong_schema"));
    }

    @Test
    void packagedPluginShouldLoadManifestDeclaredRegistryResources() throws Exception {
        Path pluginsRoot = Files.createTempDirectory("ea-plugin-packaged-registries");
        Path pluginDir = pluginsRoot.resolve("packaged-plugin-dir");
        Path libDir = pluginDir.resolve("lib");
        Files.createDirectories(libDir);

        Path jarPath = libDir.resolve("packaged-plugin.jar");
        createPluginJar(
                jarPath,
                "packaged-plugin",
                List.of(
                        "META-INF/everyarrow/packaged-plugin/custom_registry_alpha.json",
                        "META-INF/everyarrow/packaged-plugin/custom_registry_beta.json"
                ),
                Map.of(
                        "META-INF/everyarrow/packaged-plugin/custom_registry_alpha.json",
                        dbRegistryJson("test_db.public.packaged_alpha", "test_db", "packaged_alpha", "GET", "/packaged/alpha"),
                        "META-INF/everyarrow/packaged-plugin/custom_registry_beta.json",
                        dbRegistryJson("test_db.public.packaged_beta", "test_db", "packaged_beta", "POST", "/packaged/beta")
                )
        );

        Engine engine = new Engine();
        PluginManager pluginManager = new PluginManager(engine.getRegistry(), pluginsRoot.toFile());
        pluginManager.setEngine(engine);
        pluginManager.loadPlugins();

        assertNotNull(engine.getRegistry().getDefinition("test_db.public.packaged_alpha"));
        assertNotNull(engine.getRegistry().getDefinition("test_db.public.packaged_beta"));
    }

    @Test
    void packagedPluginShouldIgnoreRegistryResourcesNotDeclaredByManifest() throws Exception {
        Path pluginsRoot = Files.createTempDirectory("ea-plugin-packaged-explicit-registries");
        Path pluginDir = pluginsRoot.resolve("packaged-plugin-dir");
        Path libDir = pluginDir.resolve("lib");
        Files.createDirectories(libDir);

        String currentResource = "META-INF/everyarrow/packaged-plugin/current_registry.json";
        String legacyResource = "META-INF/everyarrow/packaged-plugin/legacy_registry.json";
        createPluginJar(
                libDir.resolve("packaged-plugin.jar"),
                "packaged-plugin",
                List.of(currentResource),
                Map.of(
                        currentResource,
                        dbRegistryJson("test_db.public.current_service", "test_db", "current_service", "GET", "/current"),
                        legacyResource,
                        dbRegistryJson("test_db.public.legacy_service", "test_db", "legacy_service", "GET", "/legacy")
                )
        );

        Engine engine = new Engine();
        PluginManager pluginManager = new PluginManager(engine.getRegistry(), pluginsRoot.toFile());
        pluginManager.setEngine(engine);
        pluginManager.loadPlugins();

        LoadedPlugin loadedPlugin = pluginManager.getLoadedPlugin("packaged-plugin-dir").orElseThrow();
        assertEquals(1, loadedPlugin.getServiceRegistrySources().size());
        assertNotNull(engine.getRegistry().getDefinition("test_db.public.current_service"));
        assertFalse(pluginManager.getDefinition("test_db.public.legacy_service").isPresent());
    }

    @Test
    void stagedDeclaredRegistryResourceShouldOverridePackagedResource() throws Exception {
        Path pluginsRoot = Files.createTempDirectory("ea-plugin-staged-over-packaged");
        Path pluginDir = pluginsRoot.resolve("packaged-plugin-dir");
        Path libDir = pluginDir.resolve("lib");
        Files.createDirectories(libDir);

        String resourcePath = "META-INF/everyarrow/packaged-plugin/current_registry.json";
        createPluginJar(
                libDir.resolve("packaged-plugin.jar"),
                "packaged-plugin",
                List.of(resourcePath),
                Map.of(resourcePath,
                        dbRegistryJson("test_db.public.stale_packaged", "test_db", "stale_packaged", "GET", "/stale"))
        );
        writeRootPluginManifest(pluginDir, "packaged-plugin", List.of(resourcePath));
        writeDbRegistry(
                pluginDir.resolve(resourcePath),
                "test_db.public.current_staged",
                "test_db",
                "current_staged",
                "POST",
                "/current");

        Engine engine = new Engine();
        PluginManager pluginManager = new PluginManager(engine.getRegistry(), pluginsRoot.toFile());
        pluginManager.setEngine(engine);
        pluginManager.loadPlugins();

        assertNotNull(engine.getRegistry().getDefinition("test_db.public.current_staged"));
        assertFalse(pluginManager.getDefinition("test_db.public.stale_packaged").isPresent());
    }

    @Test
    void pluginManifestShouldRejectUnsafePluginIdDuringDiscovery() throws Exception {
        Path pluginsRoot = Files.createTempDirectory("ea-plugin-unsafe-manifest-id");
        Path pluginDir = pluginsRoot.resolve("unsafe-plugin");
        Files.createDirectories(pluginDir);

        writeRootPluginManifest(pluginDir, "../unsafe-plugin", List.of());

        Engine engine = new Engine();
        PluginManager pluginManager = new PluginManager(engine.getRegistry(), pluginsRoot.toFile());
        pluginManager.setEngine(engine);
        pluginManager.loadPlugins();

        assertFalse(pluginManager.getLoadedPlugin("unsafe-plugin").isPresent());
    }

    @Test
    void manifestRegistryResourcePathShouldRejectTraversalDuringDiscovery() throws Exception {
        Path pluginsRoot = Files.createTempDirectory("ea-plugin-unsafe-registry-path");
        Path pluginDir = pluginsRoot.resolve("unsafe-registry-path");
        Files.createDirectories(pluginDir);

        writeRootPluginManifest(
                pluginDir,
                "unsafe-registry-path",
                List.of("META-INF/everyarrow/unsafe-registry-path/../service_registry.json"));

        Engine engine = new Engine();
        PluginManager pluginManager = new PluginManager(engine.getRegistry(), pluginsRoot.toFile());
        pluginManager.setEngine(engine);
        pluginManager.loadPlugins();

        assertFalse(pluginManager.getLoadedPlugin("unsafe-registry-path").isPresent());
    }

    @Test
    void manifestDeclaredMalformedRegistryShouldFailPluginLoad() throws Exception {
        Path pluginsRoot = Files.createTempDirectory("ea-plugin-malformed-declared-registry");
        Path pluginDir = pluginsRoot.resolve("malformed-declared-registry");
        Path libDir = pluginDir.resolve("lib");
        Files.createDirectories(libDir);

        String resourcePath = "META-INF/everyarrow/malformed-declared-registry/service_registry.json";
        createPluginJar(
                libDir.resolve("malformed-declared-registry.jar"),
                "malformed-declared-registry",
                List.of(resourcePath),
                Map.of(resourcePath, "{ not valid json")
        );

        Engine engine = new Engine();
        PluginManager pluginManager = new PluginManager(engine.getRegistry(), pluginsRoot.toFile());
        pluginManager.setEngine(engine);

        IllegalStateException error = assertThrows(IllegalStateException.class, pluginManager::loadPlugins);
        assertTrue(error.getMessage().contains("Failed to parse plugin JSON service registry"));
        assertFalse(pluginManager.getLoadedPlugin("malformed-declared-registry").isPresent());
    }

    @Test
    void manifestDeclaredEmptyRegistryShouldFailPluginLoad() throws Exception {
        Path pluginsRoot = Files.createTempDirectory("ea-plugin-empty-declared-registry");
        Path pluginDir = pluginsRoot.resolve("empty-declared-registry");
        Path libDir = pluginDir.resolve("lib");
        Files.createDirectories(libDir);

        String resourcePath = "META-INF/everyarrow/empty-declared-registry/service_registry.json";
        createPluginJar(
                libDir.resolve("empty-declared-registry.jar"),
                "empty-declared-registry",
                List.of(resourcePath),
                Map.of(resourcePath, "[]")
        );

        Engine engine = new Engine();
        PluginManager pluginManager = new PluginManager(engine.getRegistry(), pluginsRoot.toFile());
        pluginManager.setEngine(engine);

        IllegalStateException error = assertThrows(IllegalStateException.class, pluginManager::loadPlugins);
        assertTrue(error.getMessage().contains("Declared plugin registry resource did not define any services"));
        assertFalse(pluginManager.getLoadedPlugin("empty-declared-registry").isPresent());
    }

        @Test
        void samePluginIdenticalRegistryFileAndPackagedResourceShouldLoadOnce() throws Exception {
                Path pluginsRoot = Files.createTempDirectory("ea-plugin-staged-packaged-duplicate");
                Path pluginDir = pluginsRoot.resolve("perch-extension");
                Path libDir = pluginDir.resolve("lib");
                Files.createDirectories(libDir);

                String registryJson = dbRegistryJson(
                                "perch.telemetry.extension_start_group",
                                "EveryArrow",
                                "extension_start_group",
                                "POST",
                                "/perch/start-group");
                Files.writeString(pluginDir.resolve("schema_registry.json"), registryJson, StandardCharsets.UTF_8);
                createPluginJar(
                                libDir.resolve("perch.jar"),
                                "perch-extension",
                                List.of("META-INF/everyarrow/perch-extension/schema_registry.json"),
                                Map.of("META-INF/everyarrow/perch-extension/schema_registry.json", registryJson)
                );

                Engine engine = new Engine();
                PluginManager pluginManager = new PluginManager(engine.getRegistry(), pluginsRoot.toFile());
                pluginManager.setEngine(engine);
                pluginManager.loadPlugins();

                ServiceCallDefinition def = engine.getRegistry().getDefinition("perch.telemetry.extension_start_group");
                assertNotNull(def);
                assertEquals("/perch/start-group", def.getRestRoute().path);
                assertEquals(1, pluginManager.getExternalRegistryDefinitionCount());
        }

        @Test
        void pluginRequiresShouldResolveManifestPluginIdsNotDirectoryNames() throws Exception {
                Path pluginsRoot = Files.createTempDirectory("ea-plugin-requires-manifest-id");
                Path swarmDir = pluginsRoot.resolve("dittoe-swarm-controller-plugin");
                Path consoleDir = pluginsRoot.resolve("everyarrow-console-plugin");
                Files.createDirectories(swarmDir);
                Files.createDirectories(consoleDir);

                writeRootPluginManifest(swarmDir, "dittoe-swarm-controller", List.of());
                writeRootPluginManifest(consoleDir, "everyarrow-console-plugin", List.of(), List.of("dittoe-swarm-controller"));

                Engine engine = new Engine();
                PluginManager pluginManager = new PluginManager(engine.getRegistry(), pluginsRoot.toFile());
                pluginManager.setEngine(engine);
                pluginManager.loadPlugins();

                ByteArrayOutputStream stderr = new ByteArrayOutputStream();
                PrintStream originalErr = System.err;
                EnginePluginContextImpl context = new EnginePluginContextImpl();
                context.registerService(Engine.class, engine);
                try {
                        System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
                        pluginManager.startBackgroundPlugins(context);
                } finally {
                        System.setErr(originalErr);
                }

                String output = stderr.toString(StandardCharsets.UTF_8);
                assertFalse(output.contains("requires unknown plugin"), output);
        }

    @Test
    void samePluginDuplicateServiceTypeAcrossRegistryFilesShouldFailLoad() throws Exception {
        Path pluginsRoot = Files.createTempDirectory("ea-plugin-duplicate-service-type");
        Path pluginDir = pluginsRoot.resolve("duplicate-service-type");
        Files.createDirectories(pluginDir);

        writeRootPluginManifest(pluginDir, "duplicate-service-type", List.of());
        writeDbRegistry(pluginDir.resolve("service_registry_alpha.json"),
                "test_db.public.duplicate_service",
                "test_db",
                "alpha",
                "GET",
                "/duplicate/alpha");
        writeDbRegistry(pluginDir.resolve("service_registry_beta.json"),
                "test_db.public.duplicate_service",
                "test_db",
                "beta",
                "POST",
                "/duplicate/beta");

        Engine engine = new Engine();
        PluginManager pluginManager = new PluginManager(engine.getRegistry(), pluginsRoot.toFile());
        pluginManager.setEngine(engine);

        IllegalStateException error = assertThrows(IllegalStateException.class, pluginManager::loadPlugins);
        assertTrue(error.getMessage().contains("duplicate serviceType 'test_db.public.duplicate_service'"));
        assertTrue(error.getMessage().contains("service_registry_alpha.json"));
        assertTrue(error.getMessage().contains("service_registry_beta.json"));
    }

    @Test
    void samePluginDuplicateRestRouteAcrossRegistryFilesShouldFailLoad() throws Exception {
        Path pluginsRoot = Files.createTempDirectory("ea-plugin-duplicate-route");
        Path pluginDir = pluginsRoot.resolve("duplicate-route");
        Files.createDirectories(pluginDir);

        writeRootPluginManifest(pluginDir, "duplicate-route", List.of());
        writeDbRegistry(pluginDir.resolve("service_registry_alpha.json"),
                "test_db.public.route_alpha",
                "test_db",
                "route_alpha",
                "GET",
                "/duplicate/route");
        writeDbRegistry(pluginDir.resolve("service_registry_beta.json"),
                "test_db.public.route_beta",
                "test_db",
                "route_beta",
                "GET",
                "/duplicate/route");

        Engine engine = new Engine();
        PluginManager pluginManager = new PluginManager(engine.getRegistry(), pluginsRoot.toFile());
        pluginManager.setEngine(engine);

        IllegalStateException error = assertThrows(IllegalStateException.class, pluginManager::loadPlugins);
        assertTrue(error.getMessage().contains("duplicate REST route 'GET /duplicate/route'"));
    }

    @Test
    void crossPluginDuplicateServiceTypeShouldFailAsAmbiguous() throws Exception {
        Path pluginsRoot = Files.createTempDirectory("ea-plugin-cross-plugin-first-win");
        Path firstDir = pluginsRoot.resolve("a-first");
        Path secondDir = pluginsRoot.resolve("b-second");
        Files.createDirectories(firstDir);
        Files.createDirectories(secondDir);

        writeRootPluginManifest(firstDir, "first-plugin", List.of());
        writeRootPluginManifest(secondDir, "second-plugin", List.of());

        writeDbRegistry(firstDir.resolve("service_registry_alpha.json"),
                "first_db.public.shared_service",
                "first_db",
                "shared_alpha",
                "GET",
                "/shared/alpha");
        writeDbRegistry(secondDir.resolve("service_registry_alpha.json"),
                "first_db.public.shared_service",
                "first_db",
                "shared_beta",
                "POST",
                "/shared/beta");

        Engine engine = new Engine();
        PluginManager pluginManager = new PluginManager(engine.getRegistry(), pluginsRoot.toFile());
        pluginManager.setEngine(engine);
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                pluginManager::loadPlugins);

        assertTrue(error.getMessage().contains("Ambiguous cross-plugin serviceType"));
        assertTrue(error.getMessage().contains("first_db.public.shared_service"));
    }

    private void writeRootPluginManifest(Path pluginDir, String pluginId, List<String> registryResources) throws Exception {
        writeRootPluginManifest(pluginDir, pluginId, registryResources, List.of());
    }

    private void writeRootPluginManifest(
            Path pluginDir,
            String pluginId,
            List<String> registryResources,
            List<String> requires) throws Exception {
        Files.createDirectories(pluginDir);
        String registryResourcesJson = registryResources.isEmpty()
                ? ""
                : ",\n    \"registryResources\": [\n" + registryResources.stream()
                .map(value -> "      \"" + value + "\"")
                .reduce((left, right) -> left + ",\n" + right)
                .orElse("") + "\n    ]";
        String requiresJson = requires.isEmpty()
                ? ""
                : ",\n    \"requires\": [\n" + requires.stream()
                .map(value -> "      \"" + value + "\"")
                .reduce((left, right) -> left + ",\n" + right)
                .orElse("") + "\n    ]";

        Files.writeString(pluginDir.resolve("plugin.json"), """
                {
                  "plugin": {
                    "id": "%s",
                    "version": "1.0.0"%s%s
                  },
                  "services": []
                }
                """.formatted(pluginId, registryResourcesJson, requiresJson), StandardCharsets.UTF_8);
    }

    private void writeDbRegistry(
            Path path,
            String serviceType,
            String database,
            String function,
            String httpMethod,
            String routePath) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, dbRegistryJson(serviceType, database, function, httpMethod, routePath), StandardCharsets.UTF_8);
    }

    private void writeServiceCallOnlyDbRegistry(
            Path path,
            String database,
            String schema,
            String function) throws Exception {
        Files.createDirectories(path.getParent());
        String serviceType = database + "." + schema + "." + function;
        Files.writeString(path, """
                {
                  "registry": {
                    "pluginId": "service-call-only-db-plugin",
                    "database": "%s",
                    "schema": "%s",
                    "generated": true,
                    "defaultExposure": "service-call-only"
                  },
                  "schema": {
                    "database": "%s",
                    "name": "%s"
                  },
                  "services": [
                    {
                      "key": "%s",
                      "serviceType": "%s",
                      "source": "db:%s",
                      "database": "%s",
                      "schema": "%s",
                      "function": "%s",
                      "generated": true,
                      "jsonType": "json",
                      "description": "ServiceCall-only DB registry entry"
                    }
                  ]
                }
                """.formatted(
                database,
                schema,
                database,
                schema,
                serviceType,
                serviceType,
                database,
                database,
                schema,
                function), StandardCharsets.UTF_8);
    }

    private String dbRegistryJson(
            String serviceType,
            String database,
            String function,
            String httpMethod,
            String routePath) {
        return """
                [
                  {
                    "database": "%s",
                    "schema": "public",
                    "function": "%s",
                    "serviceType": "%s",
                    "httpMethod": "%s",
                    "routePath": "%s",
                    "description": "test",
                    "jsonType": "jsonb",
                    "annotations": {
                      "call": {
                        "applicationKey": "TESTS"
                      },
                      "secured": {
                        "roles": ["system"],
                        "scopes": ["tests:execute"]
                      }
                    }
                  }
                ]
                """.formatted(database, function, serviceType, httpMethod, routePath);
    }

    private void writeSchemaRegistry(
            Path path,
            String registryDatabase,
            String registrySchema,
            String database,
            String schema,
            String serviceType,
            String function,
            String httpMethod,
            String routePath) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, """
                {
                  "schema": {
                    "database": "%s",
                    "name": "%s"
                  },
                  "services": [
                    {
                      "db_name": "%s",
                      "schema_name": "%s",
                      "function_name": "%s",
                      "service_type": "%s",
                      "http_method": "%s",
                      "route_path_final": "%s",
                      "description": "test",
                      "annotations": {
                        "call": {
                          "applicationKey": "TESTS"
                        },
                        "secured": {
                          "roles": ["system"],
                          "scopes": ["tests:execute"]
                        }
                      }
                    }
                  ]
                }
                """.formatted(registryDatabase, registrySchema, database, schema, function, serviceType, httpMethod, routePath),
                StandardCharsets.UTF_8);
    }

    private void createPluginJar(
            Path jarPath,
            String pluginId,
            List<String> registryResources,
            Map<String, String> resourceContents) throws Exception {
        Files.createDirectories(jarPath.getParent());

        String manifestJson = """
                {
                  "plugin": {
                    "id": "%s",
                    "version": "1.0.0",
                    "registryResources": [
                %s
                    ]
                  },
                  "services": []
                }
                """.formatted(
                pluginId,
                registryResources.stream()
                        .map(value -> "      \"" + value + "\"")
                        .reduce((left, right) -> left + ",\n" + right)
                        .orElse("")
        );

        try (JarOutputStream jar = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
            addJarEntry(jar, "META-INF/everyarrow/plugin.json", manifestJson);
            for (Map.Entry<String, String> entry : resourceContents.entrySet()) {
                addJarEntry(jar, entry.getKey(), entry.getValue());
            }
        }
    }

    private void addJarEntry(JarOutputStream jar, String name, String content) throws Exception {
        JarEntry entry = new JarEntry(name);
        jar.putNextEntry(entry);
        jar.write(content.getBytes(StandardCharsets.UTF_8));
        jar.closeEntry();
    }
}
