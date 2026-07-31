/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.cache;

import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class BoundedTtlCache<K, V> {

    private final CachePolicy policy;
    private final LongSupplier tickerNanos;
    private final ConcurrentHashMap<K, Entry<V>> entries = new ConcurrentHashMap<>();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();

    public BoundedTtlCache(CachePolicy policy) {
        this(policy, System::nanoTime);
    }

    BoundedTtlCache(CachePolicy policy, LongSupplier tickerNanos) {
        this.policy = Objects.requireNonNull(policy, "policy is required");
        this.tickerNanos = Objects.requireNonNull(tickerNanos, "tickerNanos is required");
    }

    public V get(K key) {
        if (key == null) {
            misses.incrementAndGet();
            return null;
        }

        Entry<V> entry = entries.get(key);
        if (entry == null) {
            misses.incrementAndGet();
            return null;
        }

        if (entry.isExpired(now())) {
            entries.remove(key, entry);
            evictions.incrementAndGet();
            misses.incrementAndGet();
            return null;
        }

        hits.incrementAndGet();
        return entry.value();
    }

    public V getOrLoad(K key, Supplier<V> loader) {
        V existing = get(key);
        if (existing != null) {
            return existing;
        }

        V loaded = Objects.requireNonNull(loader, "loader is required").get();
        if (loaded != null) {
            put(key, loaded);
        }
        return loaded;
    }

    public void put(K key, V value) {
        if (key == null) {
            return;
        }
        if (value == null) {
            invalidate(key);
            return;
        }

        long now = now();
        long expiresAt = now + policy.ttl().toNanos();
        entries.put(key, new Entry<>(value, expiresAt));
        evictOverflow();
    }

    public void invalidate(K key) {
        if (key != null && entries.remove(key) != null) {
            evictions.incrementAndGet();
        }
    }

    public void clear() {
        entries.clear();
    }

    public int size() {
        return entries.size();
    }

    public CacheStats stats() {
        return new CacheStats(size(), hits.get(), misses.get(), evictions.get());
    }

    private void evictOverflow() {
        while (entries.size() > policy.maxEntries()) {
            Iterator<K> keys = entries.keySet().iterator();
            if (!keys.hasNext()) {
                return;
            }
            K key = keys.next();
            if (entries.remove(key) != null) {
                evictions.incrementAndGet();
            }
        }
    }

    private long now() {
        return tickerNanos.getAsLong();
    }

    private record Entry<V>(V value, long expiresAtNanos) {
        boolean isExpired(long nowNanos) {
            return nowNanos >= expiresAtNanos;
        }
    }
}
