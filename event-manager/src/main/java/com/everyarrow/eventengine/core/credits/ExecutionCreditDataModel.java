package com.everyarrow.eventengine.core.credits;

/**
 * Persistence target for future credit and usage data-model changes.
 */
public final class ExecutionCreditDataModel {
    public static final String DATABASE_NAME = "everyarrow_development";
    public static final String SCHEMA_NAME = "finance";
    public static final String USAGE_EVENT_TABLE = "execution_usage_event";
    public static final String RESERVATION_TABLE = "execution_credit_reservation";
    public static final String LICENSE_TABLE = "execution_license";

    private ExecutionCreditDataModel() {
    }
}
