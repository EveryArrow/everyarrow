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

import java.util.Objects;

/**
 * Immutable startup result for the Event Manager registry boundary.
 *
 * This is a snapshot of startup state. Health endpoints do not open a database
 * connection merely to report it.
 */
public record RegistryRuntimeStatus(
        EventManagerOperatingMode mode,
        Source source,
        boolean ready,
        boolean fallback,
        int definitionCount,
        String detail) {

    public enum Source {
        NOT_INITIALIZED,
        DISCOVERED_LOCAL,
        LOCAL_SNAPSHOT,
        DATABASE
    }

    public RegistryRuntimeStatus {
        mode = Objects.requireNonNull(mode, "mode is required");
        source = Objects.requireNonNull(source, "source is required");
        definitionCount = Math.max(0, definitionCount);
        detail = detail == null ? "" : detail;
    }

    public static RegistryRuntimeStatus notInitialized() {
        return new RegistryRuntimeStatus(
                EventManagerOperatingMode.AUTO,
                Source.NOT_INITIALIZED,
                false,
                false,
                0,
                "Registry initialization has not completed");
    }

    public JsonObject toJson(boolean includeDetail) {
        JsonObject json = new JsonObject();
        json.addProperty("mode", mode.name().toLowerCase());
        json.addProperty("source", source.name().toLowerCase());
        json.addProperty("ready", ready);
        json.addProperty("fallback", fallback);
        json.addProperty("definitionCount", definitionCount);
        if (includeDetail && !detail.isBlank()) {
            json.addProperty("detail", detail);
        }
        return json;
    }
}
