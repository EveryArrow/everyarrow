/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.retry;

import java.time.Duration;

/**
 * Implements a retry strategy that retries an operation at fixed intervals.
 *
 * @deprecated Use {@link RetryExecutor} with {@link RetryPolicy} and {@link BackoffSchedule#fixed(Duration)}.
 */
@Deprecated(forRemoval = false)
public class FixedIntervalRetryStrategy extends RetryStrategy {
    private final long interval;

    /**
     * Constructs a fixed interval retry strategy.
     *
     * @param interval              The fixed interval between retries in milliseconds.
     * @param maxRetries            The maximum number of total attempts.
     * @param retryConditionHandler Handler for exceptions thrown by the operation.
     */
    public FixedIntervalRetryStrategy(long interval, int maxRetries, RetryConditionHandler retryConditionHandler) {
        super(maxRetries, retryConditionHandler);
        this.interval = interval;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T retry(CheckedSupplier<T> operation) throws Exception {
        return executeWithPolicy(
                "fixed-interval-retry",
                BackoffSchedule.fixed(Duration.ofMillis(interval)),
                operation);
    }
}
