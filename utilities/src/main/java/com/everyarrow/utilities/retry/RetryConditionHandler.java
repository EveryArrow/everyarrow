/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.retry;

/**
 * RetryConditionHandler provides a method to handle exceptions and determine retry conditions.
 */
@FunctionalInterface
public interface RetryConditionHandler {
    /**
     * Handles an exception and determines whether the retry should proceed.
     *
     * @param ex      The exception to handle.
     * @param attempt The number of retry attempts.
     * @return Whether the retry should proceed.
     */
    boolean handle(Exception ex, int attempt);
}
