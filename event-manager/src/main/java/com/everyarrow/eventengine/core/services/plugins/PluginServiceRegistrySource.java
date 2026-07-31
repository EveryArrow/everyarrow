/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.plugins;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Identifies one JSON service registry source associated with a plugin.
 */
public record PluginServiceRegistrySource(SourceKind kind, String location, boolean declared) {

    public enum SourceKind {
        CLASSPATH_RESOURCE,
        FILE
    }

    public PluginServiceRegistrySource {
        Objects.requireNonNull(kind, "kind is required");
        Objects.requireNonNull(location, "location is required");
    }

    public PluginServiceRegistrySource(SourceKind kind, String location) {
        this(kind, location, false);
    }

    public static PluginServiceRegistrySource classpathResource(String resourcePath) {
        return new PluginServiceRegistrySource(SourceKind.CLASSPATH_RESOURCE, resourcePath);
    }

    public static PluginServiceRegistrySource declaredClasspathResource(String resourcePath) {
        return new PluginServiceRegistrySource(SourceKind.CLASSPATH_RESOURCE, resourcePath, true);
    }

    public static PluginServiceRegistrySource file(Path path) {
        return new PluginServiceRegistrySource(SourceKind.FILE, path.toAbsolutePath().toString());
    }

    public static PluginServiceRegistrySource declaredFile(Path path) {
        return new PluginServiceRegistrySource(SourceKind.FILE, path.toAbsolutePath().toString(), true);
    }

    public boolean isClasspathResource() {
        return kind == SourceKind.CLASSPATH_RESOURCE;
    }

    public boolean matchesResourcePath(String resourcePath) {
        String normalizedResourcePath = resourcePath.replace('\\', '/');
        if (isClasspathResource()) {
            return location.equals(normalizedResourcePath);
        }

        String normalizedLocation = location.replace('\\', '/');
        return normalizedLocation.endsWith("/" + normalizedResourcePath);
    }

    public String describe() {
        if (isClasspathResource()) {
            return "classpath resource '" + location + "'";
        }
        return "file '" + location + "'";
    }
}
