/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.retry;

/**
 * Receives retry lifecycle events for logging, metrics, or instrumentation.
 */
public interface RetryObserver {

    RetryObserver NO_OP = new RetryObserver() {
    };

    static RetryObserver noop() {
        return NO_OP;
    }

    default void onFailure(RetryAttempt attempt) {
    }

    default void onRetryScheduled(RetryAttempt attempt) {
    }

    default void onSuccess(RetryAttempt attempt) {
    }

    default void onExhausted(RetryAttempt attempt) {
    }
}
