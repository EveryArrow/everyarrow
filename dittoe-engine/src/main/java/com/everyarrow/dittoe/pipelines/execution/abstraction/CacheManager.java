package com.everyarrow.dittoe.pipelines.execution.abstraction;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CacheManager {
    private static CacheManager instance;
    private final Map<String, Map<Object, Object>> caches;

    private CacheManager() {
        caches = new ConcurrentHashMap<>();
    }

    public static synchronized CacheManager getInstance() {
        if (instance == null) {
            instance = new CacheManager();
        }
        return instance;
    }

    public void put(String cacheName, Object key, Object value) {
        Map<Object, Object> cache = caches.computeIfAbsent(cacheName, k -> new ConcurrentHashMap<>());
        cache.put(key, value);
    }

    public Object get(String cacheName, Object key) {
        Map<Object, Object> cache = caches.get(cacheName);
        return cache != null ? cache.get(key) : null;
    }

    public Object remove(String cacheName, Object key) {
        Map<Object, Object> cache = caches.get(cacheName);

        if (cache != null) {
            return cache.remove(key);
        }

        return null;
    }

    public boolean containsKey(String cacheName, Object key) {
        Map<Object, Object> cache = caches.get(cacheName);
        return cache != null && cache.containsKey(key);
    }

    public int size(String cacheName) {
        Map<Object, Object> cache = caches.get(cacheName);
        return cache != null ? cache.size() : 0;
    }
}