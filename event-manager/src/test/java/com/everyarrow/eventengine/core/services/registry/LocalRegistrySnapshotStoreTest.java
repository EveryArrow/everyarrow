/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.registry;

import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.eventengine.core.services.metadata.HealthCheckHandler;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.google.gson.JsonObject;
import com.everyarrow.utilities.environment.Environment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalRegistrySnapshotStoreTest {

    @TempDir
    Path tempDirectory;

    @AfterEach
    void resetEnvironment() {
        Environment.reset();
    }

    @Test
    void savesAndLoadsDatabaseRegistryRepresentation() throws Exception {
        Path snapshotPath = tempDirectory.resolve("registry.json");
        LocalRegistrySnapshotStore store = new LocalRegistrySnapshotStore(snapshotPath);
        ServiceCallDefinition definition = definition("sample.tools.run");

        store.save("server-a", List.of(definition));

        List<ServiceCallDefinition> loaded = store.load("server-a").orElseThrow();
        assertEquals(1, loaded.size());
        assertEquals("sample.tools.run", loaded.getFirst().getServiceType());
        assertEquals("sample.Handler", loaded.getFirst().getHandlerClass());
        assertEquals("plugin:sample", loaded.getFirst().getSource());
    }

    @Test
    void rejectsSnapshotOwnedByAnotherServerSurface() throws Exception {
        LocalRegistrySnapshotStore store = new LocalRegistrySnapshotStore(tempDirectory.resolve("registry.json"));
        store.save("server-a", List.of(definition("sample.tools.run")));

        assertThrows(IOException.class, () -> store.load("server-b"));
    }

    @Test
    void localModeInitializesFromSnapshotWithoutUsingConfiguredDatabase() throws Exception {
        Path snapshotPath = tempDirectory.resolve("registry.json");
        LocalRegistrySnapshotStore store = new LocalRegistrySnapshotStore(snapshotPath);
        store.save("local-test", List.of(definition("snapshot.local.run")));

        Environment.put("EA_EVENT_MANAGER_MODE", "local");
        Environment.put("EA_REGISTRY_SNAPSHOT_PATH", snapshotPath.toString());
        Environment.put("EA_EVENT_MANAGER_SURFACE_ID", "local-test");
        Environment.put("METADB.DB_HOST", "database-that-must-not-be-contacted.invalid");
        Environment.put("METADB.DB_NAME", "EveryArrow");
        Environment.put("METADB.DB_USERNAME", "unused");
        Environment.put("METADB.DB_PASSWORD", "unused");

        Engine engine = new Engine(tempDirectory.resolve("plugins").toFile());
        engine.initialize();

        assertNotNull(engine.getRegistry().getDefinition("snapshot.local.run"));
        assertEquals(RegistryRuntimeStatus.Source.LOCAL_SNAPSHOT,
                engine.getRegistryRuntimeStatus().source());
        assertTrue(engine.getRegistryRuntimeStatus().ready());
        assertFalse(engine.getRegistryRuntimeStatus().fallback());
    }

    @Test
    void localModeStartsAndStopsWithoutDatabaseConnectivity() throws Exception {
        Environment.put("EA_EVENT_MANAGER_MODE", "local");
        Environment.put("EA_REGISTRY_SNAPSHOT_PATH", tempDirectory.resolve("missing-registry.json").toString());
        Environment.put("EA_QUEUE_ENABLED", "false");
        Environment.put("METADB.DB_HOST", "database-that-must-not-be-contacted.invalid");
        Environment.put("METADB.DB_NAME", "EveryArrow");
        Environment.put("METADB.DB_USERNAME", "unused");
        Environment.put("METADB.DB_PASSWORD", "unused");

        Engine engine = new Engine(tempDirectory.resolve("plugins").toFile());
        try {
            engine.initialize();
            engine.start();

            assertTrue(engine.isInitialized());
            assertTrue(engine.isRunning());
            assertEquals(RegistryRuntimeStatus.Source.DISCOVERED_LOCAL,
                    engine.getRegistryRuntimeStatus().source());

            JsonObject health = new HealthCheckHandler(engine).execute(new JsonObject(), "healthCheck", "test");
            assertEquals("UP", health.get("status").getAsString());
            assertEquals("local", health.get("registryMode").getAsString());
            assertEquals("discovered_local", health.get("registrySource").getAsString());
            assertFalse(health.get("registryFallback").getAsBoolean());
        } finally {
            engine.shutdown();
        }

        assertFalse(engine.isRunning());
    }

    @Test
    void databaseModeFallsBackLocallyButReportsNotReadyWithoutMetaDb() throws Exception {
        Path snapshotPath = tempDirectory.resolve("database-fallback.json");
        new LocalRegistrySnapshotStore(snapshotPath)
                .save("database-fallback", List.of(definition("snapshot.database.fallback")));

        Environment.put("EA_EVENT_MANAGER_MODE", "database");
        Environment.put("EA_REGISTRY_SNAPSHOT_PATH", snapshotPath.toString());
        Environment.put("EA_EVENT_MANAGER_SURFACE_ID", "database-fallback");

        Engine engine = new Engine(tempDirectory.resolve("plugins-database").toFile());
        engine.initialize();

        assertNotNull(engine.getRegistry().getDefinition("snapshot.database.fallback"));
        assertEquals(RegistryRuntimeStatus.Source.LOCAL_SNAPSHOT,
                engine.getRegistryRuntimeStatus().source());
        assertTrue(engine.getRegistryRuntimeStatus().fallback());
        assertFalse(engine.getRegistryRuntimeStatus().ready());
    }

    @Test
    void autoModeTreatsLocalFallbackAsReadyWithoutMetaDb() throws Exception {
        Environment.put("EA_EVENT_MANAGER_MODE", "auto");
        Environment.put("EA_REGISTRY_SNAPSHOT_PATH", tempDirectory.resolve("missing-auto.json").toString());

        Engine engine = new Engine(tempDirectory.resolve("plugins-auto").toFile());
        engine.initialize();

        assertEquals(RegistryRuntimeStatus.Source.DISCOVERED_LOCAL,
                engine.getRegistryRuntimeStatus().source());
        assertTrue(engine.getRegistryRuntimeStatus().fallback());
        assertTrue(engine.getRegistryRuntimeStatus().ready());
    }

    private ServiceCallDefinition definition(String serviceType) {
        ServiceCallDefinition definition = new ServiceCallDefinition();
        definition.setServiceType(serviceType);
        definition.setHandlerClass("sample.Handler");
        definition.setClassName("sample.Handler");
        definition.setCategory("Sample");
        definition.setSource("plugin:sample");
        definition.annotations.call = new ServiceCallDefinition.CallMeta();
        definition.annotations.call.applicationKey = "SAMPLE";
        return definition;
    }
}
