package com.everyarrow.eventengine.core.credits;

import com.everyarrow.eventengine.core.services.runtime.ServiceCallSource;

/**
 * Billable execution categories understood by Event Manager.
 */
public enum ExecutionType {
    REST_SERVICE_CALL,
    WEBHOOK_SERVICE_CALL,
    QUEUE_MESSAGE,
    SCHEDULED_JOB,
    WEBSOCKET_COMMAND,
    INTERNAL_SERVICE_CALL,
    PIPELINE_RUN;

    public static ExecutionType fromServiceCallSource(ServiceCallSource source) {
        return switch (source == null ? ServiceCallSource.INTERNAL : source) {
            case REST -> REST_SERVICE_CALL;
            case WEBHOOK -> WEBHOOK_SERVICE_CALL;
            case QUEUE -> QUEUE_MESSAGE;
            case SCHEDULE -> SCHEDULED_JOB;
            case WEBSOCKET -> WEBSOCKET_COMMAND;
            case INTERNAL, FILE, POSTGRES_NOTIFICATION -> INTERNAL_SERVICE_CALL;
        };
    }
}
