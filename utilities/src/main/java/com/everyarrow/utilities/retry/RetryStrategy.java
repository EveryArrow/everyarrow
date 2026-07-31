/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.retry;

import java.util.Objects;

/**
 * Defines the abstract strategy for retrying an operation.
 *
 * @deprecated Use {@link RetryExecutor} with {@link RetryPolicy}.
 */
@Deprecated(forRemoval = false)
public abstract class RetryStrategy {
    protected final int maxRetries;
    protected final RetryConditionHandler retryConditionHandler;

    /**
     * Constructs a retry strategy.
     *
     * @param maxRetries            The maximum number of total attempts.
     * @param retryConditionHandler Handler for exceptions thrown by the operation.
     */
    public RetryStrategy(int maxRetries, RetryConditionHandler retryConditionHandler) {
        this.maxRetries = maxRetries;
        this.retryConditionHandler = retryConditionHandler;
    }

    /**
     * Attempts to retry the given operation based on a specified condition.
     *
     * @param operation The operation to be executed
     * @param <T>       The type of the result returned by the operation
     * @return The result of the operation
     * @throws Exception If the operation fails after the maximum retries or the retry condition prohibits further attempts
     */
    public abstract <T> T retry(CheckedSupplier<T> operation) throws Exception;

    protected <T> T executeWithPolicy(String name,
                                      BackoffSchedule backoffSchedule,
                                      CheckedSupplier<T> operation) throws Exception {
        RetryPolicy policy = RetryPolicy.builder()
                .name(name)
                .maxAttempts(maxRetries)
                .backoffSchedule(backoffSchedule)
                .retryClassifier(this::isRetryable)
                .build();

        return RetryExecutor.of(policy).execute(Objects.requireNonNull(operation, "operation"));
    }

    private boolean isRetryable(RetryAttempt attempt) {
        return retryConditionHandler == null
                || retryConditionHandler.handle(attempt.getLastFailure(), attempt.getAttemptNumber());
    }
}
