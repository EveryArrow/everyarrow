package com.everyarrow.eventengine.core.credits;

/**
 * Thrown by execution credit gates when an execution requires an upgrade,
 * license refresh, or more credits.
 */
public final class ExecutionCreditDeniedException extends RuntimeException {
    private final CreditDenialReason reason;
    private final long creditsRequired;
    private final long creditsAvailable;

    public ExecutionCreditDeniedException(CreditDenialReason reason,
                                          long creditsRequired,
                                          long creditsAvailable,
                                          String message) {
        super(message == null || message.isBlank() ? "Execution credit denied" : message.trim());
        if (reason == null) {
            throw new IllegalArgumentException("reason is required");
        }
        if (creditsRequired < 0 || creditsAvailable < 0) {
            throw new IllegalArgumentException("credit counts must be non-negative");
        }
        this.reason = reason;
        this.creditsRequired = creditsRequired;
        this.creditsAvailable = creditsAvailable;
    }

    public CreditDenialReason getReason() {
        return reason;
    }

    public long getCreditsRequired() {
        return creditsRequired;
    }

    public long getCreditsAvailable() {
        return creditsAvailable;
    }
}
