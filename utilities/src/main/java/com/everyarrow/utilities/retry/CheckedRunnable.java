/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.retry;

/**
 * Runnable variant that allows checked exceptions.
 */
@FunctionalInterface
public interface CheckedRunnable {
    void run() throws Exception;
}
