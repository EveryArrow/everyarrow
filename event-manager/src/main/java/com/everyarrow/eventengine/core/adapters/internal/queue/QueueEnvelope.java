package com.everyarrow.eventengine.core.adapters.internal.queue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Canonical queue envelope for Event Manager.
 *
 * Required fields:
 * - serviceType: registry key to dispatch
 *
 * Optional:
 * - v: version (int)
 * - params: object
 */
public final class QueueEnvelope {

    private QueueEnvelope() {}

    public static JsonObject parse(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("Queue message body is empty");
        var el = JsonParser.parseString(raw);
        if (el == null || !el.isJsonObject()) throw new IllegalArgumentException("Queue envelope must be a JSON object");
        return el.getAsJsonObject();
    }

    public static String requireServiceType(JsonObject env) {
        if (!env.has("serviceType")) throw new IllegalArgumentException("Queue envelope missing: serviceType");
        String v = env.get("serviceType").getAsString();
        if (v == null || v.isBlank()) throw new IllegalArgumentException("Queue envelope blank: serviceType");
        return v;
    }

    public static JsonObject paramsOrEmpty(JsonObject env) {
        if (env.has("params") && env.get("params").isJsonObject()) return env.getAsJsonObject("params");
        return new JsonObject();
    }
}
