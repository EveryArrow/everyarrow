/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.plugins;

import java.util.regex.Pattern;

final class PluginPathSecurity {

    private static final Pattern SAFE_PLUGIN_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
    private static final String REGISTRY_FILE_SUFFIX = ".json";
    private static final String PLUGIN_DOCUMENT_FILE_NAME = "plugin.json";

    private PluginPathSecurity() {
    }

    static String validatePluginId(String pluginId) {
        String normalized = requireNonBlank(pluginId, "plugin.id");
        if (!SAFE_PLUGIN_ID.matcher(normalized).matches()) {
            throw new IllegalStateException("Plugin id contains unsafe characters: " + normalized);
        }
        return normalized;
    }

    static String normalizeRegistryResourcePath(String resourcePath) {
        String normalized = requireNonBlank(resourcePath, "plugin.registryResources entry");
        if (normalized.startsWith("/")
                || normalized.contains("\\")
                || normalized.contains(":")
                || normalized.contains("//")) {
            throw new IllegalStateException("Plugin registry resource path is unsafe: " + normalized);
        }

        String[] segments = normalized.split("/");
        for (String segment : segments) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalStateException("Plugin registry resource path is unsafe: " + normalized);
            }
        }

        String fileName = segments[segments.length - 1];
        if (!fileName.endsWith(REGISTRY_FILE_SUFFIX)
                || PLUGIN_DOCUMENT_FILE_NAME.equalsIgnoreCase(fileName)) {
            throw new IllegalStateException("Plugin registry resource must name a service registry JSON file: "
                    + normalized);
        }

        return normalized;
    }

    private static String requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Plugin metadata is missing " + label);
        }
        return value.trim();
    }
}
