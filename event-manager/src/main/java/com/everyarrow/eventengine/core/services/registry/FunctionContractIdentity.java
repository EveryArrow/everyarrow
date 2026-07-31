/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.registry;

import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Immutable runtime view of a function's declared compatibility contract.
 */
public record FunctionContractIdentity(
        String namespace,
        String version,
        String signature,
        List<String> capabilities,
        String pluginId,
        String pluginVersion) {

    public FunctionContractIdentity {
        capabilities = capabilities == null
                ? List.of()
                : capabilities.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    static FunctionContractIdentity from(ServiceCallDefinition definition) {
        if (definition == null
                || definition.getAnnotations() == null
                || definition.getAnnotations().functionContract == null) {
            return null;
        }
        ServiceCallDefinition.FunctionContractMeta metadata =
                definition.getAnnotations().functionContract;
        return new FunctionContractIdentity(
                trimToNull(metadata.namespace),
                trimToNull(metadata.version),
                trimToNull(metadata.signature),
                metadata.capabilities,
                trimToNull(metadata.pluginId),
                trimToNull(metadata.pluginVersion));
    }

    String declarationKey() {
        return String.join("\u001F",
                blankToEmpty(namespace),
                blankToEmpty(version),
                blankToEmpty(signature),
                String.join("\u001E", capabilities),
                blankToEmpty(pluginId),
                blankToEmpty(pluginVersion));
    }

    JsonObject toJson() {
        JsonObject out = new JsonObject();
        add(out, "namespace", namespace);
        add(out, "version", version);
        add(out, "signature", signature);
        JsonArray capabilityArray = new JsonArray();
        capabilities.forEach(capabilityArray::add);
        out.add("capabilities", capabilityArray);
        add(out, "pluginId", pluginId);
        add(out, "pluginVersion", pluginVersion);
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
