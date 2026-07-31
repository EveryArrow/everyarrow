/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.plugins;

import com.everyarrow.eventengine.core.services.registry.ServiceRegistry;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.everyarrow.eventengine.spi.plugin.EnginePluginContext;
import com.everyarrow.eventengine.spi.plugin.PluginHealthResult;
import com.everyarrow.utilities.environment.Environment;

import java.util.ArrayList;

/**
 * Runs plugin health checks and moves unhealthy plugins into quarantine.
 */
final class PluginHealthRegistry {

    private final PluginCatalog pluginCatalog;
    private final ServiceRegistry registry;
    private final PluginIndex pluginIndex;

    PluginHealthRegistry(PluginCatalog pluginCatalog, ServiceRegistry registry, PluginIndex pluginIndex) {
        this.pluginCatalog = pluginCatalog;
        this.registry = registry;
        this.pluginIndex = pluginIndex;
    }

    RemovedPluginRegistrations runDefaultHealthChecks(EnginePluginContext ctx, boolean running) {
        RemovedPluginRegistrations removedRegistrations = RemovedPluginRegistrations.none();
        for (LoadedPlugin plugin : new ArrayList<>(pluginCatalog.loadedPlugins())) {
            PluginHealthResult result = defaultHealth(plugin, ctx, running);
            plugin.setHealthResult(result);
            if (result.isDown()) {
                removedRegistrations = removedRegistrations.plus(quarantinePlugin(plugin, result));
            }
        }
        return removedRegistrations;
    }

    RemovedPluginRegistrations runBackgroundHealthChecks(EnginePluginContext ctx) {
        RemovedPluginRegistrations removedRegistrations = RemovedPluginRegistrations.none();
        for (LoadedPlugin plugin : new ArrayList<>(pluginCatalog.loadedPlugins())) {
            if (plugin.getBackgroundPlugin().isEmpty()) {
                continue;
            }

            String pluginId = plugin.getEffectivePluginId();
            PluginHealthResult custom;
            try {
                custom = plugin.getBackgroundPlugin().get().health(ctx).withPluginId(pluginId);
            } catch (Exception e) {
                custom = PluginHealthResult.down(pluginId, "background-health", e.getMessage());
            }

            PluginHealthResult customResult = custom;
            PluginHealthResult merged = plugin.getHealthResult()
                    .map(existing -> existing.merge(customResult))
                    .orElse(custom);
            plugin.setHealthResult(merged);
            if (merged.isDown()) {
                removedRegistrations = removedRegistrations.plus(quarantinePlugin(plugin, merged));
            }
        }
        return removedRegistrations;
    }

    PluginHealthResult defaultHealth(LoadedPlugin plugin, EnginePluginContext ctx, boolean running) {
        String pluginId = plugin.getEffectivePluginId();
        PluginHealthResult.Builder builder = PluginHealthResult.builder(pluginId);

        if (plugin.getRegistryDocument().isPresent()) {
            builder.up("manifest", "plugin.json loaded");
            PluginMetadata metadata = plugin.getPluginMetadata().orElse(null);
            checkDeclaredRegistryResources(plugin, metadata, builder);
            checkBackgroundClass(plugin, metadata, builder);
            checkRequiredServices(plugin, metadata, builder);
            checkRequiredConfig(plugin, metadata, ctx, running, builder);
        } else {
            builder.warn("manifest", "No plugin.json manifest present");
        }

        if (plugin.isRequiredPlatformPlugin()) {
            builder.up("plugin-directory", "Built-in platform plugin");
        } else if (plugin.getDescriptor().getPluginDirectory() != null
                && plugin.getDescriptor().getPluginDirectory().isDirectory()) {
            builder.up("plugin-directory", "Plugin directory exists");
        } else {
            builder.down("plugin-directory", "Plugin directory is missing");
        }

        if (running && hasRuntimeAdapterDefinitions(plugin)) {
            builder.warn("adapter-refresh", "Queue or calendar definitions require restart in this cut");
            builder.restartRequired(true);
            plugin.setRestartRequired(true);
        }

        return builder.build();
    }

    RemovedPluginRegistrations quarantinePlugin(LoadedPlugin plugin, PluginHealthResult result) {
        String pluginId = plugin.getEffectivePluginId();
        plugin.setQuarantined(true);
        plugin.setHealthResult(result);
        pluginCatalog.quarantine(plugin);
        removePluginRegistrations(plugin);
        System.err.printf("[PluginManager] Quarantined plugin '%s': %s%n", pluginId, result.getStatus());
        int definitionCount = plugin.getRegisteredServiceTypes().size();
        return new RemovedPluginRegistrations(definitionCount, 0);
    }

    private void checkDeclaredRegistryResources(LoadedPlugin plugin,
                                                PluginMetadata metadata,
                                                PluginHealthResult.Builder builder) {
        if (metadata == null || metadata.getRegistryResources().isEmpty()) {
            builder.up("registry-resources", "No declared registry resources");
            return;
        }

        for (String resource : metadata.getRegistryResources()) {
            String normalized = PluginPathSecurity.normalizeRegistryResourcePath(resource);
            boolean loaded = plugin.getServiceRegistrySources().stream()
                    .anyMatch(source -> source.matchesResourcePath(normalized));
            if (loaded) {
                builder.up("registry-resource:" + normalized, "Registry resource loaded");
            } else {
                builder.down("registry-resource:" + normalized, "Declared registry resource was not loaded");
            }
        }
    }

    private void checkBackgroundClass(LoadedPlugin plugin,
                                      PluginMetadata metadata,
                                      PluginHealthResult.Builder builder) {
        if (metadata == null || isBlank(metadata.getBackgroundClass())) {
            builder.up("background-class", "No background class declared");
            return;
        }

        if (plugin.getBackgroundPlugin().isPresent()) {
            builder.up("background-class", metadata.getBackgroundClass() + " instantiated");
        } else {
            builder.down("background-class", metadata.getBackgroundClass() + " was not instantiated");
        }
    }

    private void checkRequiredServices(LoadedPlugin plugin, PluginMetadata metadata, PluginHealthResult.Builder builder) {
        if (metadata == null || metadata.getHealth().getRequiredServices().isEmpty()) {
            builder.up("required-services", "No required services declared");
            return;
        }

        for (String serviceType : metadata.getHealth().getRequiredServices()) {
            if (registry.getDefinition(serviceType) != null || pluginIndex.getDefinition(serviceType).isPresent()) {
                builder.up("required-service:" + serviceType, "Required service is registered");
            } else if (hasDatabaseOwnedRegistryResources(plugin, metadata)) {
                builder.warn("required-service:" + serviceType,
                        "Required service is awaiting event_manager.registry_functions row");
            } else {
                builder.down("required-service:" + serviceType, "Required service is missing");
            }
        }
    }

    private boolean hasDatabaseOwnedRegistryResources(LoadedPlugin plugin, PluginMetadata metadata) {
        return plugin != null
                && metadata != null
                && metadata.getRegistryResources() != null
                && !metadata.getRegistryResources().isEmpty();
    }

    private void checkRequiredConfig(LoadedPlugin plugin,
                                     PluginMetadata metadata,
                                     EnginePluginContext ctx,
                                     boolean running,
                                     PluginHealthResult.Builder builder) {
        if (metadata == null || metadata.getHealth().getRequiredConfig().isEmpty()) {
            builder.up("required-config", "No required config declared");
            return;
        }

        for (String key : metadata.getHealth().getRequiredConfig()) {
            String value = resolveConfig(ctx, key);
            if (isBlank(value)) {
                if (plugin != null && plugin.isRequiredPlatformPlugin() && !running) {
                    builder.warn("required-config:" + key, "Required config is missing before runtime start");
                } else {
                    builder.down("required-config:" + key, "Required config is missing");
                }
            } else {
                builder.up("required-config:" + key, "Required config is present");
            }
        }
    }

    private String resolveConfig(EnginePluginContext ctx, String key) {
        String property = System.getProperty(key);
        if (!isBlank(property)) {
            return property;
        }
        if (ctx != null) {
            String value = ctx.get(key);
            if (!isBlank(value)) {
                return value;
            }
        }
        return Environment.getInstance().get(key, "");
    }

    private boolean hasRuntimeAdapterDefinitions(LoadedPlugin plugin) {
        for (String serviceType : plugin.getRegisteredServiceTypes()) {
            ServiceCallDefinition def = registry.getDefinition(serviceType);
            if (def == null) {
                def = pluginIndex.getDefinition(serviceType).orElse(null);
            }
            if (def == null || def.getAnnotations() == null) {
                continue;
            }
            if (def.hasQueueSubscriptions()) {
                return true;
            }
            if (def.getAnnotations().calendarJobs != null && !def.getAnnotations().calendarJobs.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void removePluginRegistrations(LoadedPlugin plugin) {
        registry.deregisterPlugin(plugin.getEffectivePluginId());
        for (String serviceType : plugin.getRegisteredServiceTypes()) {
            pluginIndex.removeServiceType(serviceType);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    record RemovedPluginRegistrations(int externalDefinitionCount, int platformDefinitionCount) {

        private static RemovedPluginRegistrations none() {
            return new RemovedPluginRegistrations(0, 0);
        }

        private RemovedPluginRegistrations plus(RemovedPluginRegistrations other) {
            if (other == null) {
                return this;
            }
            return new RemovedPluginRegistrations(
                    externalDefinitionCount + other.externalDefinitionCount,
                    platformDefinitionCount + other.platformDefinitionCount);
        }
    }
}
