/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.plugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple registry mapping plugin ID to its class loader.
 */
public class PluginRegistry {

    private final Map<String, PluginClassLoader> loaders = new ConcurrentHashMap<>();

    public void register(PluginClassLoader loader) {
        loaders.put(loader.getDescriptor().getId(), loader);
    }

    public void unregister(String id) throws Exception {
        PluginClassLoader loader = loaders.remove(id);
        if (loader != null) {
            loader.close();
        }
    }

    public Map<String, PluginClassLoader> getAll() {
        return loaders;
    }

    public void clear() throws Exception {
        for (PluginClassLoader loader : loaders.values()) {
            loader.close();
        }
        loaders.clear();
    }
}
