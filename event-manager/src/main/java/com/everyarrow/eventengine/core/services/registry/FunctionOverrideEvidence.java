/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.registry;

import com.google.gson.JsonObject;

/**
 * Diagnostic evidence for a declared function override.
 */
public record FunctionOverrideEvidence(
        Status status,
        String targetServiceType,
        String targetPluginId,
        String targetPluginVersion,
        String targetFunctionVersion,
        String targetImplementation,
        String versionRange,
        boolean namespaceCompatible,
        boolean signatureCompatible,
        boolean capabilitiesCompatible,
        boolean versionCompatible) {

    public enum Status {
        VALID,
        PENDING_TARGET
    }

    JsonObject toJson() {
        JsonObject out = new JsonObject();
        out.addProperty("status", status.name().toLowerCase());
        add(out, "targetServiceType", targetServiceType);
        add(out, "targetPluginId", targetPluginId);
        add(out, "targetPluginVersion", targetPluginVersion);
        add(out, "targetFunctionVersion", targetFunctionVersion);
        add(out, "targetImplementation", targetImplementation);
        add(out, "versionRange", versionRange);
        out.addProperty("namespaceCompatible", namespaceCompatible);
        out.addProperty("signatureCompatible", signatureCompatible);
        out.addProperty("capabilitiesCompatible", capabilitiesCompatible);
        out.addProperty("versionCompatible", versionCompatible);
        return out;
    }

    private static void add(JsonObject object, String property, String value) {
        if (value != null) {
            object.addProperty(property, value);
        }
    }
}
