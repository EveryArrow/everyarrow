package com.everyarrow.eventengine.core.credits;

import com.everyarrow.eventengine.core.security.EveryArrowServicePrefixes;

/**
 * Public Event Manager service names for execution-credit status surfaces.
 */
public final class ExecutionCreditServiceContract {
    public static final String APPLICATION_KEY = "EVENT_MANAGER";
    public static final String SERVICE_PREFIX = EveryArrowServicePrefixes.EVENT_MANAGER_API;

    public static final String STATUS = SERVICE_PREFIX + ".execution_credit_status";
    public static final String USAGE_SUMMARY = SERVICE_PREFIX + ".execution_usage_summary";
    public static final String LICENSE_IMPORT = SERVICE_PREFIX + ".execution_license_import";

    private ExecutionCreditServiceContract() {
    }
}
