/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.security;

/**
 * Canonical service prefixes for the unified EveryArrow database.
 *
 * The MVP is physically stored in the EveryArrow database, but service names
 * keep their product ownership instead of collapsing into one namespace.
 */
public final class EveryArrowServicePrefixes {

    public static final String DATABASE = "EveryArrow";

    public static final String APP_SECURITY_SCHEMA = "app_security";
    public static final String PERCH_SCHEMA = "perch";
    public static final String CONSOLE_SCHEMA = "console";
    public static final String CONNECTOR_SCHEMA = "connector";
    public static final String PIPELINE_SCHEMA = "pipeline";
    public static final String EVENT_MANAGER_SCHEMA = "event_manager";
    public static final String REGISTRY_SCHEMA = "registry";
    public static final String ENTITLEMENT_SCHEMA = "entitlement";
    public static final String MODEL_SCHEMA = "model";
    public static final String SCHEDULER_SCHEMA = "scheduler";
    public static final String IDENTITY_PROVIDER_SCHEMA = "identity_provider";

    public static final String SECURITY_ACCESS_CONTROL = "security.access_control";
    public static final String PERCH_TELEMETRY = "perch.telemetry";
    public static final String DITTOE_SWARM_PIPELINE = "dittoe_swarm.pipeline";
    public static final String EVENT_MANAGER_API = "event_manager.api";
    public static final String CONSOLE = "everyarrow.console";
    public static final String CONNECTOR = "connector";
    public static final String REGISTRY = "registry";
    public static final String ENTITLEMENT = "entitlement";
    public static final String MODEL = "model";
    public static final String SCHEDULER = "scheduler";
    public static final String IDENTITY_PROVIDER = "identity_provider";

    /**
     * Compatibility aliases for in-flight refactor code. Prefer the product
     * names above in new code.
     */
    public static final String APP_SECURITY = SECURITY_ACCESS_CONTROL;
    public static final String TELEMETRY_SCHEMA = PERCH_SCHEMA;
    public static final String TELEMETRY = PERCH_TELEMETRY;
    public static final String PIPELINE = DITTOE_SWARM_PIPELINE;
    public static final String EVENT_MANAGER = EVENT_MANAGER_API;

    private EveryArrowServicePrefixes() {
    }

    public static String service(String prefix, String functionName) {
        return prefix + "." + functionName;
    }
}
