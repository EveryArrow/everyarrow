/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.plugins;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime catalog of active and quarantined plugins.
 */
final class PluginCatalog {

    private final ConcurrentHashMap<String, LoadedPlugin> loadedPlugins = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LoadedPlugin> quarantinedPlugins = new ConcurrentHashMap<>();

    void addLoaded(LoadedPlugin plugin) {
        loadedPlugins.put(plugin.getDescriptor().getPluginId(), plugin);
    }

    LoadedPlugin getLoaded(String pluginId) {
        return loadedPlugins.get(pluginId);
    }

    LoadedPlugin getQuarantined(String pluginId) {
        return quarantinedPlugins.get(pluginId);
    }

    Optional<LoadedPlugin> findLoaded(String pluginId) {
        return Optional.ofNullable(getLoaded(pluginId));
    }

    Optional<LoadedPlugin> findQuarantined(String pluginId) {
        return Optional.ofNullable(getQuarantined(pluginId));
    }

    Collection<LoadedPlugin> loadedPlugins() {
        return loadedPlugins.values();
    }

    Collection<LoadedPlugin> quarantinedPlugins() {
        return quarantinedPlugins.values();
    }

    Set<String> loadedPluginNames() {
        return loadedPlugins.keySet();
    }

    Set<String> quarantinedPluginNames() {
        return quarantinedPlugins.keySet();
    }

    boolean isKnown(String pluginId) {
        return loadedPlugins.containsKey(pluginId) || quarantinedPlugins.containsKey(pluginId);
    }

    boolean hasLoaded(String pluginId) {
        return loadedPlugins.containsKey(pluginId);
    }

    boolean hasQuarantined(String pluginId) {
        return quarantinedPlugins.containsKey(pluginId);
    }

    void quarantine(LoadedPlugin plugin) {
        String mapKey = plugin.getDescriptor().getPluginId();
        loadedPlugins.remove(mapKey);
        quarantinedPlugins.put(mapKey, plugin);
    }

    void clear() {
        loadedPlugins.clear();
        quarantinedPlugins.clear();
    }
}
