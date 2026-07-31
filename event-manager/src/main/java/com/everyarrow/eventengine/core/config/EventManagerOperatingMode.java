/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.config;

import com.everyarrow.utilities.environment.Environment;

import java.util.Locale;

/**
 * Selects whether Event Manager may use database-backed platform capabilities.
 */
public enum EventManagerOperatingMode {
    AUTO,
    LOCAL,
    DATABASE;

    public static final String CONFIG = "EA_EVENT_MANAGER_MODE";

    public static EventManagerOperatingMode fromEnvironment() {
        return from(Environment.getInstance());
    }

    static EventManagerOperatingMode from(Environment environment) {
        Environment env = environment == null ? Environment.getInstance() : environment;
        String configured = env.get(CONFIG, "auto");
        String normalized = configured == null ? "auto" : configured.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "", "auto" -> AUTO;
            case "local" -> LOCAL;
            case "database", "db" -> DATABASE;
            default -> throw new IllegalArgumentException(
                    "Unsupported " + CONFIG + " value: " + configured
                            + ". Expected 'auto', 'local', or 'database'.");
        };
    }

    public boolean isLocal() {
        return this == LOCAL;
    }
}
