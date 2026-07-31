/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.tests.plugins;

import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.eventengine.core.hooks.SecurityHook;
import com.everyarrow.utilities.environment.Environment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionalPlatformPluginTest {

    @AfterEach
    void resetEnvironment() {
        Environment.reset();
    }

    @Test
    void coreLoadsWithoutSecurityOrPerchPlugins(@TempDir Path pluginRoot) throws Exception {
        Environment.put("EA_EVENT_MANAGER_MODE", "local");

        Engine engine = new Engine(pluginRoot.toFile());
        engine.initialize();

        assertFalse(engine.getPluginManager().getLoadedPlugin("security").isPresent());
        assertFalse(engine.getPluginManager().getLoadedPlugin("perch").isPresent());
        assertTrue(engine.getPluginManager().areRequiredPlatformPluginsReady());
        assertTrue(engine.getHookManager().hasHook(SecurityHook.class));
    }

    @Test
    void surfaceCanRequireMissingInternalPlugins(@TempDir Path pluginRoot) throws Exception {
        Environment.put("EA_EVENT_MANAGER_MODE", "local");
        Environment.put("EA_REQUIRED_PLUGIN_IDS", "security,perch");

        Engine engine = new Engine(pluginRoot.toFile());
        engine.initialize();

        assertFalse(engine.getPluginManager().areRequiredPlatformPluginsReady());
        assertTrue(engine.getPluginManager().readinessJson()
                .getAsJsonArray("missingRequiredPlugins")
                .asList()
                .stream()
                .anyMatch(value -> "security".equals(value.getAsString())));
    }
}
