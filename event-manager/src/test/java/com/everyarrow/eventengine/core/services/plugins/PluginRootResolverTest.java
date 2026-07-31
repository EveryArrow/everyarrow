/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.plugins;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PluginRootResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void explicitSystemPropertyValueWins() throws Exception {
        Path propertyRoot = Files.createDirectory(tempDir.resolve("property-root"));
        Path envRoot = Files.createDirectory(tempDir.resolve("env-root"));
        Path runtimeRoot = createRuntimePlugins();
        Path cwd = createWorkingDirectoryWithPlugins();

        Path resolved = PluginRootResolver.resolvePath(
                propertyRoot.toString(),
                envRoot.toString(),
                runtimeRoot.resolve("event-engine.jar"),
                cwd);

        assertEquals(propertyRoot.toAbsolutePath().normalize(), resolved);
    }

    @Test
    void environmentValueIsUsedWhenPropertyIsAbsent() throws Exception {
        Path envRoot = Files.createDirectory(tempDir.resolve("env-root"));
        Path runtimeRoot = createRuntimePlugins();
        Path cwd = createWorkingDirectoryWithPlugins();

        Path resolved = PluginRootResolver.resolvePath(
                "",
                envRoot.toString(),
                runtimeRoot.resolve("event-engine.jar"),
                cwd);

        assertEquals(envRoot.toAbsolutePath().normalize(), resolved);
    }

    @Test
    void jarAdjacentPluginsArePreferredOverWorkingDirectoryPlugins() throws Exception {
        Path runtimeRoot = createRuntimePlugins();
        Path cwd = createWorkingDirectoryWithPlugins();

        Path resolved = PluginRootResolver.resolvePath(
                "",
                "",
                runtimeRoot.resolve("event-engine.jar"),
                cwd);

        assertEquals(runtimeRoot.resolve("plugins").toAbsolutePath().normalize(), resolved);
    }

    @Test
    void classesAdjacentPluginsArePreferredOverWorkingDirectoryPlugins() throws Exception {
        Path runtimeRoot = createRuntimePlugins();
        Path classesDirectory = Files.createDirectories(runtimeRoot.resolve("classes"));
        Path cwd = createWorkingDirectoryWithPlugins();

        Path resolved = PluginRootResolver.resolvePath(
                "",
                "",
                classesDirectory,
                cwd);

        assertEquals(runtimeRoot.resolve("plugins").toAbsolutePath().normalize(), resolved);
    }

    @Test
    void workingDirectoryPluginsAreUsedWhenRuntimeAdjacentPluginsAreMissing() throws Exception {
        Path runtimeRoot = Files.createDirectory(tempDir.resolve("runtime"));
        Path cwd = createWorkingDirectoryWithPlugins();

        Path resolved = PluginRootResolver.resolvePath(
                "",
                "",
                runtimeRoot.resolve("event-engine.jar"),
                cwd);

        assertEquals(cwd.resolve("plugins").toAbsolutePath().normalize(), resolved);
    }

    @Test
    void missingDefaultRootFallsBackToWorkingDirectoryPluginsPath() throws Exception {
        Path runtimeRoot = Files.createDirectory(tempDir.resolve("runtime"));
        Path cwd = Files.createDirectory(tempDir.resolve("cwd"));

        Path resolved = PluginRootResolver.resolvePath(
                "",
                "",
                runtimeRoot.resolve("event-engine.jar"),
                cwd);

        assertEquals(cwd.resolve("plugins").toAbsolutePath().normalize(), resolved);
    }

    private Path createRuntimePlugins() throws Exception {
        Path runtimeRoot = Files.createDirectory(tempDir.resolve("runtime-" + System.nanoTime()));
        Files.createDirectory(runtimeRoot.resolve("plugins"));
        return runtimeRoot;
    }

    private Path createWorkingDirectoryWithPlugins() throws Exception {
        Path cwd = Files.createDirectory(tempDir.resolve("cwd-" + System.nanoTime()));
        Files.createDirectory(cwd.resolve("plugins"));
        return cwd;
    }
}
