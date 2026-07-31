/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.retry;

import java.time.Duration;
import java.util.Objects;

/**
 * Calculates the delay after a failed attempt before the next attempt starts.
 */
@FunctionalInterface
public interface BackoffSchedule {

    Duration delayFor(int failedAttemptNumber);

    static BackoffSchedule noDelay() {
        return attempt -> Duration.ZERO;
    }

    static BackoffSchedule fixed(Duration delay) {
        Duration safeDelay = requireNonNegative(delay, "delay");
        return attempt -> safeDelay;
    }

    static BackoffSchedule linear(Duration initialDelay, Duration increment) {
        Duration safeInitialDelay = requireNonNegative(initialDelay, "initialDelay");
        Duration safeIncrement = requireNonNegative(increment, "increment");
        return attempt -> safeInitialDelay.plus(safeIncrement.multipliedBy(Math.max(0, attempt - 1L)));
    }

    static BackoffSchedule exponential(Duration initialDelay) {
        return exponential(initialDelay, 2.0d);
    }

    static BackoffSchedule exponential(Duration initialDelay, double multiplier) {
        Duration safeInitialDelay = requireNonNegative(initialDelay, "initialDelay");
        if (multiplier < 1.0d) {
            throw new IllegalArgumentException("multiplier must be >= 1.0");
        }

        return attempt -> {
            long factor = Math.round(Math.pow(multiplier, Math.max(0, attempt - 1L)));
            try {
                return safeInitialDelay.multipliedBy(Math.max(1L, factor));
            } catch (ArithmeticException ex) {
                return Duration.ofMillis(Long.MAX_VALUE);
            }
        };
    }

    private static Duration requireNonNegative(Duration value, String name) {
        Duration duration = Objects.requireNonNull(value, name);
        if (duration.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return duration;
    }
}
