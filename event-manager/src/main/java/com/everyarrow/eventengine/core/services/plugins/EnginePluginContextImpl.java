/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.plugins;

import com.everyarrow.eventengine.spi.plugin.EnginePluginContext;
import com.everyarrow.utilities.environment.Environment;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Engine-owned implementation of {@link EnginePluginContext}.
 *
 * One instance is created per engine lifecycle and shared across all
 * background plugin {@code onStart} calls.  This means all cross-plugin
 * service registrations are visible to all plugins through the same map.
 */
public class EnginePluginContextImpl implements EnginePluginContext {

    private final ConcurrentHashMap<Class<?>, Object> services = new ConcurrentHashMap<>();

    @Override
    public String get(String key) {
        String property = System.getProperty(key);
        if (property != null && !property.isBlank()) {
            return property;
        }
        return Environment.getInstance().get(key);
    }

    @Override
    public String get(String key, String defaultValue) {
        String property = System.getProperty(key);
        if (property != null && !property.isBlank()) {
            return property;
        }
        return Environment.getInstance().get(key, defaultValue);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getService(Class<T> serviceType) {
        return (T) services.get(serviceType);
    }

    @Override
    public <T> void registerService(Class<T> type, T impl) {
        if (impl == null) {
            throw new IllegalArgumentException("Cannot register null service for type: " + type.getName());
        }
        services.put(type, impl);
    }
}
