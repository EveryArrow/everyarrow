/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.retry;

import java.time.Duration;

/**
 * Implements a retry strategy with linearly increasing retry intervals.
 *
 * @deprecated Use {@link RetryExecutor} with {@link RetryPolicy} and {@link BackoffSchedule#linear(Duration, Duration)}.
 */
@Deprecated(forRemoval = false)
public class LinearBackoffRetryStrategy extends RetryStrategy {
    private final long initialInterval;
    private final long increment;

    /**
     * Constructs a linear backoff retry strategy.
     *
     * @param initialInterval       The initial delay before the first retry, in milliseconds.
     * @param increment             The amount by which the retry interval increases after each attempt.
     * @param maxRetries            The maximum number of total attempts.
     * @param retryConditionHandler Handler for exceptions thrown by the operation.
     */
    public LinearBackoffRetryStrategy(long initialInterval, long increment, int maxRetries, RetryConditionHandler retryConditionHandler) {
        super(maxRetries, retryConditionHandler);
        this.initialInterval = initialInterval;
        this.increment = increment;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T retry(CheckedSupplier<T> operation) throws Exception {
        return executeWithPolicy(
                "linear-backoff-retry",
                BackoffSchedule.linear(Duration.ofMillis(initialInterval), Duration.ofMillis(increment)),
                operation);
    }
}
