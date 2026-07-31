/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.retry;

import java.util.Objects;

/**
 * Immutable retry policy for in-process external-boundary operations.
 */
public final class RetryPolicy {
    private static final String DEFAULT_NAME = "retry";
    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    private final String name;
    private final int maxAttempts;
    private final BackoffSchedule backoffSchedule;
    private final RetryClassifier retryClassifier;
    private final RetryObserver retryObserver;

    private RetryPolicy(Builder builder) {
        this.name = normalizeName(builder.name);
        this.maxAttempts = validateMaxAttempts(builder.maxAttempts);
        this.backoffSchedule = Objects.requireNonNull(builder.backoffSchedule, "backoffSchedule");
        this.retryClassifier = Objects.requireNonNull(builder.retryClassifier, "retryClassifier");
        this.retryObserver = Objects.requireNonNull(builder.retryObserver, "retryObserver");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static RetryPolicy defaultPolicy() {
        return builder().build();
    }

    public String getName() {
        return name;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public BackoffSchedule getBackoffSchedule() {
        return backoffSchedule;
    }

    public RetryClassifier getRetryClassifier() {
        return retryClassifier;
    }

    public RetryObserver getRetryObserver() {
        return retryObserver;
    }

    private static String normalizeName(String name) {
        return name == null || name.isBlank() ? DEFAULT_NAME : name.trim();
    }

    private static int validateMaxAttempts(int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        return maxAttempts;
    }

    public static final class Builder {
        private String name = DEFAULT_NAME;
        private int maxAttempts = DEFAULT_MAX_ATTEMPTS;
        private BackoffSchedule backoffSchedule = BackoffSchedule.noDelay();
        private RetryClassifier retryClassifier = RetryClassifier.alwaysRetry();
        private RetryObserver retryObserver = RetryObserver.noop();

        private Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder maxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
            return this;
        }

        public Builder backoffSchedule(BackoffSchedule backoffSchedule) {
            this.backoffSchedule = backoffSchedule;
            return this;
        }

        public Builder retryClassifier(RetryClassifier retryClassifier) {
            this.retryClassifier = retryClassifier;
            return this;
        }

        public Builder retryObserver(RetryObserver retryObserver) {
            this.retryObserver = retryObserver;
            return this;
        }

        public RetryPolicy build() {
            return new RetryPolicy(this);
        }
    }
}
