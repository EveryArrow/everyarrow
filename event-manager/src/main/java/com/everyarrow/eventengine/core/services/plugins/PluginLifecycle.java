/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.plugins;

import com.everyarrow.eventengine.spi.plugin.BackgroundPlugin;
import com.everyarrow.eventengine.spi.plugin.EnginePluginContext;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * Background plugin instantiation, startup order, and shutdown state.
 */
final class PluginLifecycle {

    private final List<LoadedPlugin> startedBackgroundPlugins = new ArrayList<>();

    void instantiateBackgroundPlugins(Collection<LoadedPlugin> plugins) {
        for (LoadedPlugin plugin : plugins) {
            instantiateBackgroundPlugin(plugin);
        }
    }

    void instantiateBackgroundPlugin(LoadedPlugin plugin) {
        PluginMetadata metadata = plugin.getPluginMetadata().orElse(null);
        if (metadata == null) {
            return;
        }
        String bgClass = metadata.getBackgroundClass();
        if (bgClass == null || bgClass.isBlank()) {
            return;
        }
        try {
            Class<?> clazz = plugin.getClassLoader().loadClass(bgClass);
            BackgroundPlugin instance = (BackgroundPlugin) clazz.getDeclaredConstructor().newInstance();
            plugin.setBackgroundPlugin(instance);
            System.out.printf("[PluginManager] Instantiated background plugin: %s (%s)%n",
                    plugin.getDescriptor().getPluginId(), bgClass);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to instantiate background plugin class '" + bgClass
                            + "' for plugin '" + plugin.getDescriptor().getPluginId() + "'", e);
        }
    }

    void startBackgroundPlugins(Collection<LoadedPlugin> plugins, EnginePluginContext ctx) {
        List<LoadedPlugin> ordered = topoSort(plugins);
        for (LoadedPlugin plugin : ordered) {
            if (plugin.getBackgroundPlugin().isEmpty()) {
                continue;
            }
            startBackgroundPlugin(plugin, ctx);
        }
    }

    void preStartBackgroundPlugins(Collection<LoadedPlugin> plugins, EnginePluginContext ctx) {
        List<LoadedPlugin> ordered = topoSort(plugins);
        for (LoadedPlugin plugin : ordered) {
            if (plugin.getBackgroundPlugin().isEmpty()) {
                continue;
            }
            preStartBackgroundPlugin(plugin, ctx);
        }
    }

    void preStartBackgroundPlugin(LoadedPlugin plugin, EnginePluginContext ctx) {
        if (plugin.getBackgroundPlugin().isEmpty()) {
            return;
        }
        BackgroundPlugin bp = plugin.getBackgroundPlugin().get();
        String pluginId = plugin.getDescriptor().getPluginId();
        try {
            bp.onPreStart(ctx);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to pre-start background plugin: '" + pluginId + "'", e);
        }
    }

    void startBackgroundPlugin(LoadedPlugin plugin, EnginePluginContext ctx) {
        if (plugin.getBackgroundPlugin().isEmpty()) {
            return;
        }
        BackgroundPlugin bp = plugin.getBackgroundPlugin().get();
        String pluginId = plugin.getDescriptor().getPluginId();
        try {
            bp.onStart(ctx);
            startedBackgroundPlugins.add(plugin);
            System.out.printf("[PluginManager] Started background plugin: %s%n", pluginId);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to start background plugin: '" + pluginId + "'", e);
        }
    }

    void stopBackgroundPlugins() {
        List<LoadedPlugin> toStop = new ArrayList<>(startedBackgroundPlugins);
        Collections.reverse(toStop);
        for (LoadedPlugin plugin : toStop) {
            if (plugin.getBackgroundPlugin().isEmpty()) {
                continue;
            }
            String pluginId = plugin.getDescriptor().getPluginId();
            try {
                plugin.getBackgroundPlugin().get().onStop();
                System.out.printf("[PluginManager] Stopped background plugin: %s%n", pluginId);
            } catch (Exception e) {
                System.err.printf("[PluginManager] Error stopping background plugin '%s': %s%n",
                        pluginId, e.getMessage());
            }
        }
        startedBackgroundPlugins.clear();
    }

    private List<LoadedPlugin> topoSort(Collection<LoadedPlugin> plugins) {
        Map<String, LoadedPlugin> byId = new LinkedHashMap<>();
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        Map<String, List<String>> dependents = new LinkedHashMap<>();

        for (LoadedPlugin plugin : plugins) {
            String id = plugin.getEffectivePluginId();
            byId.put(id, plugin);
            inDegree.put(id, 0);
            dependents.put(id, new ArrayList<>());
        }

        for (LoadedPlugin plugin : plugins) {
            String id = plugin.getEffectivePluginId();
            for (String dependency : resolveRequires(plugin)) {
                if (!byId.containsKey(dependency)) {
                    System.err.printf("[PluginManager] Warning: plugin '%s' requires unknown plugin '%s'%n",
                            id, dependency);
                    continue;
                }
                inDegree.merge(id, 1, (current, increment) -> current + increment);
                dependents.get(dependency).add(id);
            }
        }

        Queue<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<LoadedPlugin> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String id = queue.poll();
            result.add(byId.get(id));
            for (String dependent : dependents.get(id)) {
                int remaining = inDegree.merge(dependent, -1, (current, decrement) -> current + decrement);
                if (remaining == 0) {
                    queue.add(dependent);
                }
            }
        }

        if (result.size() < byId.size()) {
            throw new IllegalStateException(
                    "Circular dependency detected in plugin \"requires\" graph. "
                            + "Check plugin.json \"requires\" fields.");
        }

        return result;
    }

    private List<String> resolveRequires(LoadedPlugin plugin) {
        return plugin.getPluginMetadata()
                .map(PluginMetadata::getRequires)
                .orElse(Collections.emptyList());
    }
}
