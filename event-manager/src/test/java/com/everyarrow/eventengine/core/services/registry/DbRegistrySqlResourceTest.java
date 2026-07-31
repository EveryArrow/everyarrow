/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.registry;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DbRegistrySqlResourceTest {

    @Test
    void packagesBidirectionalRegistryContractWithEmptySurfaceDeactivation() throws Exception {
        try (var stream = getClass().getResourceAsStream("/database/event_manager_registry.sql")) {
            assertNotNull(stream, "Event Manager must package its PostgreSQL registry contract");
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(sql.contains("event_manager.load_registry_surface"));
            assertTrue(sql.contains("event_manager.refresh_registry_surface"));
            assertTrue(sql.contains("missing_from_surface = true"));
            assertTrue(sql.contains("is_active = false"));
            assertTrue(sql.contains("deactivated_at = now()"));
            assertTrue(sql.contains("procedure.proargtypes[0]"));
            assertTrue(sql.contains("registry_function.database_name = current_database()"));
            assertTrue(sql.contains("'missing_function_count'"));
            assertTrue(sql.contains("registry_entry_kind <> 'db_function'"));
            assertFalse(sql.contains(
                            "registry_function.registry_entry_kind IN ('java_handler', 'annotation_metadata')"),
                    "All entries removed from an explicit surface must be deactivated");
            assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS registry_entry_kind"));
            assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS surface_id"));
            assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS missing_from_surface"));
            assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS last_seen_at"));
            assertTrue(sql.contains(
                            "existing_registry_function.registry_entry_kind = 'db_function'"));
            assertTrue(sql.contains(
                            "EXCLUDED.registry_entry_kind IN ('java_handler', 'annotation_metadata')"),
                    "Local annotations must not overwrite the durable DB-function fallback");
            assertFalse(sql.contains("IF jsonb_array_length(v_entries) = 0 THEN"),
                    "An empty surface must still deactivate definitions no longer discovered");
        }
    }
}
