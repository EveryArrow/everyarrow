/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.retry;

import java.time.Duration;

/**
 * Implements a retry strategy with exponentially increasing retry intervals.
 *
 * @deprecated Use {@link RetryExecutor} with {@link RetryPolicy} and {@link BackoffSchedule#exponential(Duration)}.
 */
@Deprecated(forRemoval = false)
public class ExponentialBackoffRetryStrategy extends RetryStrategy {
    private final long initialInterval;

    /**
     * Constructs an exponential backoff retry strategy.
     *
     * @param initialInterval       The initial interval before the first retry, in milliseconds.
     * @param maxRetries            The maximum number of total attempts.
     * @param retryConditionHandler Handler for exceptions thrown by the operation.
     */
    public ExponentialBackoffRetryStrategy(long initialInterval, int maxRetries, RetryConditionHandler retryConditionHandler) {
        super(maxRetries, retryConditionHandler);
        this.initialInterval = initialInterval;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T retry(CheckedSupplier<T> operation) throws Exception {
        return executeWithPolicy(
                "exponential-backoff-retry",
                BackoffSchedule.exponential(Duration.ofMillis(initialInterval)),
                operation);
    }
}
