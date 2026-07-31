/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.cache;

public record CacheStats(int size, long hits, long misses, long evictions) {
}
