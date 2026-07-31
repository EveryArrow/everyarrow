/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.cache;

import java.time.Duration;

public record CachePolicy(int maxEntries, Duration ttl) {

    public CachePolicy {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
    }

    public static CachePolicy of(int maxEntries, Duration ttl) {
        return new CachePolicy(maxEntries, ttl);
    }
}
