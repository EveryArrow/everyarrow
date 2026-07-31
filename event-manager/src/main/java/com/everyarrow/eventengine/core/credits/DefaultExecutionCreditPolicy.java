package com.everyarrow.eventengine.core.credits;

/**
 * V1 execution pricing used by local and cloud gates until a plan-specific
 * policy overrides it.
 */
public final class DefaultExecutionCreditPolicy implements ExecutionCreditPolicy {
    public static final long REST_SERVICE_CALL_CREDITS = 1;
    public static final long WEBHOOK_SERVICE_CALL_CREDITS = 1;
    public static final long INTERNAL_SERVICE_CALL_CREDITS = 1;
    public static final long QUEUE_MESSAGE_CREDITS = 3;
    public static final long SCHEDULED_JOB_CREDITS = 3;
    public static final long WEBSOCKET_COMMAND_CREDITS = 10;
    public static final long MINIMUM_PIPELINE_CREDITS = 1;
    public static final long PIPELINE_ROWS_PER_CREDIT = 1_000;

    @Override
    public long reservedCreditsFor(ExecutionCreditRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (!request.billable()) {
            return 0;
        }
        return switch (request.executionType()) {
            case REST_SERVICE_CALL -> REST_SERVICE_CALL_CREDITS;
            case WEBHOOK_SERVICE_CALL -> WEBHOOK_SERVICE_CALL_CREDITS;
            case INTERNAL_SERVICE_CALL -> INTERNAL_SERVICE_CALL_CREDITS;
            case QUEUE_MESSAGE -> QUEUE_MESSAGE_CREDITS;
            case SCHEDULED_JOB -> SCHEDULED_JOB_CREDITS;
            case WEBSOCKET_COMMAND -> WEBSOCKET_COMMAND_CREDITS;
            case PIPELINE_RUN -> MINIMUM_PIPELINE_CREDITS;
        };
    }

    @Override
    public long pipelineCredits(long rowsRead, long rowsWritten) {
        if (rowsRead < 0 || rowsWritten < 0) {
            throw new IllegalArgumentException("row counts must be non-negative");
        }
        long billableRows = Math.max(rowsRead, rowsWritten);
        long roundedCredits = (billableRows + PIPELINE_ROWS_PER_CREDIT - 1) / PIPELINE_ROWS_PER_CREDIT;
        return Math.max(MINIMUM_PIPELINE_CREDITS, roundedCredits);
    }
}
