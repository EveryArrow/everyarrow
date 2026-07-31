/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.retry;

import java.time.Duration;
import java.util.Objects;

/**
 * Executes operations according to a RetryPolicy.
 */
public final class RetryExecutor {
    private final RetryPolicy policy;
    private final RetrySleeper sleeper;

    public RetryExecutor(RetryPolicy policy) {
        this(policy, RetryExecutor::sleepThread);
    }

    RetryExecutor(RetryPolicy policy, RetrySleeper sleeper) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
    }

    public static RetryExecutor of(RetryPolicy policy) {
        return new RetryExecutor(policy);
    }

    public <T> T execute(CheckedSupplier<T> operation) throws Exception {
        Objects.requireNonNull(operation, "operation");

        for (int attemptNumber = 1; attemptNumber <= policy.getMaxAttempts(); attemptNumber++) {
            try {
                T result = operation.get();
                policy.getRetryObserver().onSuccess(successAttempt(attemptNumber));
                return result;
            } catch (Exception failure) {
                RetryAttempt attempt = failureAttempt(attemptNumber, failure);
                policy.getRetryObserver().onFailure(attempt);

                boolean retryable = policy.getRetryClassifier().shouldRetry(attempt);
                if (attempt.isFinalAttempt() || !retryable) {
                    policy.getRetryObserver().onExhausted(attempt);
                    throw failure;
                }

                policy.getRetryObserver().onRetryScheduled(attempt);
                sleep(attempt.getNextDelay());
            }
        }

        throw new IllegalStateException("Retry loop exited unexpectedly");
    }

    public void run(CheckedRunnable operation) throws Exception {
        Objects.requireNonNull(operation, "operation");
        execute(() -> {
            operation.run();
            return null;
        });
    }

    private RetryAttempt successAttempt(int attemptNumber) {
        return new RetryAttempt(policy.getName(), attemptNumber, policy.getMaxAttempts(), null, Duration.ZERO);
    }

    private RetryAttempt failureAttempt(int attemptNumber, Exception failure) {
        Duration delay = attemptNumber < policy.getMaxAttempts()
                ? policy.getBackoffSchedule().delayFor(attemptNumber)
                : Duration.ZERO;
        return new RetryAttempt(policy.getName(), attemptNumber, policy.getMaxAttempts(), failure, delay);
    }

    private void sleep(Duration delay) throws InterruptedException {
        if (delay == null || delay.isZero() || delay.isNegative()) {
            return;
        }

        try {
            sleeper.sleep(delay);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ex;
        }
    }

    private static void sleepThread(Duration delay) throws InterruptedException {
        Thread.sleep(delay.toMillis());
    }
}

@FunctionalInterface
interface RetrySleeper {
    void sleep(Duration delay) throws InterruptedException;
}
