/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.retry;

import java.time.Duration;

/**
 * Immutable context for one retry attempt.
 */
public final class RetryAttempt {
    private final String policyName;
    private final int attemptNumber;
    private final int maxAttempts;
    private final Exception lastFailure;
    private final Duration nextDelay;

    RetryAttempt(String policyName,
                 int attemptNumber,
                 int maxAttempts,
                 Exception lastFailure,
                 Duration nextDelay) {
        this.policyName = policyName;
        this.attemptNumber = attemptNumber;
        this.maxAttempts = maxAttempts;
        this.lastFailure = lastFailure;
        this.nextDelay = nextDelay == null ? Duration.ZERO : nextDelay;
    }

    public String getPolicyName() {
        return policyName;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public Exception getLastFailure() {
        return lastFailure;
    }

    public Duration getNextDelay() {
        return nextDelay;
    }

    public boolean isFinalAttempt() {
        return attemptNumber >= maxAttempts;
    }
}
