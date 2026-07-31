/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.retry;

/**
 * Decides whether a failed operation should be retried.
 */
@FunctionalInterface
public interface RetryClassifier {
    boolean shouldRetry(RetryAttempt attempt);

    static RetryClassifier alwaysRetry() {
        return attempt -> true;
    }

    static RetryClassifier neverRetry() {
        return attempt -> false;
    }
}
