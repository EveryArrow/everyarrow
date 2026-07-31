/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.retry;

/**
 * Functional interface similar to Supplier but allows throwing exceptions.
 */
@FunctionalInterface
public interface CheckedSupplier<T> {
    T get() throws Exception;
}
