/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.plugin;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Minimal descriptor for a plugin JAR.
 */
public class PluginDescriptor {

    private final String id;
    private final Path jarPath;
    private final Instant discoveredAt = Instant.now();

    public PluginDescriptor(String id, Path jarPath) {
        this.id = id;
        this.jarPath = jarPath;
    }

    public String getId() {
        return id;
    }

    public Path getJarPath() {
        return jarPath;
    }

    public Instant getDiscoveredAt() {
        return discoveredAt;
    }
}
