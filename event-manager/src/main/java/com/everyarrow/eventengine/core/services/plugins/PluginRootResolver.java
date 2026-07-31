/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.plugins;

import com.everyarrow.utilities.environment.Environment;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves the Event Manager plugin root.
 */
public final class PluginRootResolver {

    public static final String CONFIG_KEY = "EA_PLUGIN_ROOT";

    private static final String PLUGINS_DIRECTORY_NAME = "plugins";

    private PluginRootResolver() {
    }

    public static File resolve() {
        String propertyValue = System.getProperty(CONFIG_KEY);
        String environmentValue = Environment.getInstance().get(CONFIG_KEY, "");
        Path runtimeLocation = runtimeLocation();
        Path workingDirectory = Path.of("").toAbsolutePath();

        return resolvePath(propertyValue, environmentValue, runtimeLocation, workingDirectory).toFile();
    }

    static Path resolvePath(String propertyValue,
                            String environmentValue,
                            Path runtimeLocation,
                            Path workingDirectory) {
        Path configured = configuredPath(propertyValue);
        if (configured != null) {
            return configured;
        }

        configured = configuredPath(environmentValue);
        if (configured != null) {
            return configured;
        }

        Path runtimePlugins = runtimeBase(runtimeLocation).resolve(PLUGINS_DIRECTORY_NAME).toAbsolutePath().normalize();
        if (Files.isDirectory(runtimePlugins)) {
            return runtimePlugins;
        }

        Path cwdPlugins = workingDirectory.toAbsolutePath().normalize().resolve(PLUGINS_DIRECTORY_NAME);
        if (Files.isDirectory(cwdPlugins)) {
            return cwdPlugins;
        }

        return cwdPlugins;
    }

    private static Path configuredPath(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return Path.of(value.trim()).toAbsolutePath().normalize();
    }

    private static Path runtimeBase(Path runtimeLocation) {
        Path normalized = runtimeLocation.toAbsolutePath().normalize();
        if (Files.isRegularFile(normalized) || normalized.getFileName().toString().endsWith(".jar")) {
            Path parent = normalized.getParent();
            return parent == null ? normalized : parent;
        }

        Path parent = normalized.getParent();
        return parent == null ? normalized : parent;
    }

    private static Path runtimeLocation() {
        try {
            URI location = PluginRootResolver.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI();
            return Path.of(location);
        } catch (Exception e) {
            return Path.of("").toAbsolutePath();
        }
    }
}
