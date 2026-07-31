/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.perch;

import com.everyarrow.eventengine.core.security.EveryArrowServicePrefixes;

/**
 * Product-owned Perch runtime contract.
 *
 * Perch currently runs from the unified EveryArrow database for the MVP, but
 * the service namespace remains Perch-owned so it can be released as its own
 * SaaS surface.
 */
public final class PerchServiceContract {

    public static final String DATABASE = EveryArrowServicePrefixes.DATABASE;
    public static final String SCHEMA = EveryArrowServicePrefixes.PERCH_SCHEMA;
    public static final String SERVICE_PREFIX = EveryArrowServicePrefixes.PERCH_TELEMETRY;

    private PerchServiceContract() {
    }

    public static String service(String functionName) {
        return SERVICE_PREFIX + "." + functionName;
    }
}
