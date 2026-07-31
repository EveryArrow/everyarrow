package com.everyarrow.eventengine.core.credits;

/**
 * Final usage facts known after execution finishes.
 */
public record ExecutionUsageFinalization(
        ExecutionOutcome outcome,
        long creditsCharged,
        long rowsRead,
        long rowsWritten,
        String message) {

    public ExecutionUsageFinalization {
        if (outcome == null) {
            throw new IllegalArgumentException("outcome is required");
        }
        if (creditsCharged < 0 || rowsRead < 0 || rowsWritten < 0) {
            throw new IllegalArgumentException("usage counts must be non-negative");
        }
        message = message == null || message.isBlank() ? null : message.trim();
    }
}
