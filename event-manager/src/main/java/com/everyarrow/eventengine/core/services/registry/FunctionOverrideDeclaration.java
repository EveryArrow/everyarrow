/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.registry;

import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.google.gson.JsonObject;

/**
 * Immutable declaration of one intentional generated-function override.
 */
public record FunctionOverrideDeclaration(
        String targetServiceType,
        String targetPluginId,
        String versionRange) {

    static FunctionOverrideDeclaration from(ServiceCallDefinition definition) {
        if (definition == null
                || definition.getAnnotations() == null
                || definition.getAnnotations().functionOverride == null) {
            return null;
        }
        ServiceCallDefinition.FunctionOverrideMeta metadata =
                definition.getAnnotations().functionOverride;
        return new FunctionOverrideDeclaration(
                trimToNull(metadata.targetServiceType),
                trimToNull(metadata.targetPluginId),
                trimToNull(metadata.versionRange));
    }

    String declarationKey() {
        return String.join("\u001F",
                blankToEmpty(targetServiceType),
                blankToEmpty(targetPluginId),
                blankToEmpty(versionRange));
    }

    JsonObject toJson() {
        JsonObject out = new JsonObject();
        add(out, "targetServiceType", targetServiceType);
        add(out, "targetPluginId", targetPluginId);
        add(out, "versionRange", versionRange);
        return out;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static void add(JsonObject object, String property, String value) {
        if (value != null) {
            object.addProperty(property, value);
        }
    }
}
