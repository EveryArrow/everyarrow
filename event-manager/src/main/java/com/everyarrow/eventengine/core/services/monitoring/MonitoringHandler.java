/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.monitoring;

import com.everyarrow.eventengine.spi.handler.ServiceCallHandler;
import com.google.gson.JsonObject;

/**
 * Codex: Placeholder for the EveryArrow PerchClient integration.
 *
 * This handler will eventually emit monitoring data once the Perch plugin is available.
 * For now, it returns a simple acknowledgement response to confirm registration.
 */
public class MonitoringHandler implements ServiceCallHandler {

    @Override
    public JsonObject execute(JsonObject params, String typeName, String serviceType) {
        JsonObject result = new JsonObject();
        result.addProperty("status", "stub");
        result.addProperty("message", "MonitoringHandler active but Perch plugin not yet implemented.");
        result.add("input", params);
        return result;
    }
}
