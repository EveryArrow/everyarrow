/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BoundedTtlCacheTest {

    private final AtomicLong now = new AtomicLong();

    @Test
    void returnsCachedValueBeforeTtlExpires() {
        BoundedTtlCache<String, String> cache = cache(2, Duration.ofSeconds(1));

        cache.put("a", "one");

        assertEquals("one", cache.get("a"));
        assertEquals(new CacheStats(1, 1, 0, 0), cache.stats());
    }

    @Test
    void expiresValueAfterTtl() {
        BoundedTtlCache<String, String> cache = cache(2, Duration.ofMillis(10));

        cache.put("a", "one");
        now.addAndGet(Duration.ofMillis(11).toNanos());

        assertNull(cache.get("a"));
        assertEquals(0, cache.size());
        assertEquals(1, cache.stats().misses());
    }

    @Test
    void boundsEntryCount() {
        BoundedTtlCache<String, String> cache = cache(2, Duration.ofSeconds(1));

        cache.put("a", "one");
        cache.put("b", "two");
        cache.put("c", "three");

        assertEquals(2, cache.size());
        assertEquals(1, cache.stats().evictions());
    }

    @Test
    void getOrLoadStoresNonNullLoadedValue() {
        BoundedTtlCache<String, String> cache = cache(2, Duration.ofSeconds(1));

        assertEquals("one", cache.getOrLoad("a", () -> "one"));
        assertEquals("one", cache.getOrLoad("a", () -> "two"));
    }

    @Test
    void invalidateRemovesOneEntry() {
        BoundedTtlCache<String, String> cache = cache(2, Duration.ofSeconds(1));

        cache.put("a", "one");
        cache.invalidate("a");

        assertNull(cache.get("a"));
        assertEquals(0, cache.size());
    }

    @Test
    void clearRemovesAllEntries() {
        BoundedTtlCache<String, String> cache = cache(2, Duration.ofSeconds(1));

        cache.put("a", "one");
        cache.put("b", "two");
        cache.clear();

        assertEquals(0, cache.size());
    }

    private BoundedTtlCache<String, String> cache(int maxEntries, Duration ttl) {
        return new BoundedTtlCache<>(CachePolicy.of(maxEntries, ttl), now::get);
    }
}
