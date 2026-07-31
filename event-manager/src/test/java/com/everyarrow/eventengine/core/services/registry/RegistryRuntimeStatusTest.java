/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.registry;

import com.everyarrow.eventengine.core.config.EventManagerOperatingMode;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistryRuntimeStatusTest {

    @Test
    void publicViewReportsModeAndSourceWithoutOperationalDetail() {
        RegistryRuntimeStatus status = new RegistryRuntimeStatus(
                EventManagerOperatingMode.AUTO,
                RegistryRuntimeStatus.Source.LOCAL_SNAPSHOT,
                true,
                true,
                12,
                "database connection details must remain private");

        JsonObject publicView = status.toJson(false);

        assertEquals("auto", publicView.get("mode").getAsString());
        assertEquals("local_snapshot", publicView.get("source").getAsString());
        assertTrue(publicView.get("ready").getAsBoolean());
        assertTrue(publicView.get("fallback").getAsBoolean());
        assertEquals(12, publicView.get("definitionCount").getAsInt());
        assertFalse(publicView.has("detail"));
    }

    @Test
    void detailedViewIncludesSanitizedStartupExplanation() {
        RegistryRuntimeStatus status = new RegistryRuntimeStatus(
                EventManagerOperatingMode.DATABASE,
                RegistryRuntimeStatus.Source.DISCOVERED_LOCAL,
                false,
                true,
                4,
                "Database mode fell back locally");

        JsonObject detailedView = status.toJson(true);

        assertFalse(detailedView.get("ready").getAsBoolean());
        assertEquals("Database mode fell back locally", detailedView.get("detail").getAsString());
    }
}
