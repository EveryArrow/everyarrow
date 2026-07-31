/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.plugins;

import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.eventengine.core.services.registry.ServiceRegistry;
import com.everyarrow.eventengine.core.services.runtime.GenericProcessHandler;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.everyarrow.eventengine.spi.plugin.EnginePluginContext;
import com.everyarrow.eventengine.spi.plugin.PluginHealthResult;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Discovers ServiceCallHandler implementations in:
 *  - core classpath (com.everyarrow.eventengine.core.services.*)
 *  - plugin directories in ./plugins containing one or more JARs
 *
 * Compatibility facade for plugin discovery, registration, readiness,
 * health, file watching, and background lifecycle collaborators.
 */
public class PluginManager {

    public static final String PLUGIN_WATCH_ENABLED_CONFIG = "EA_PLUGIN_WATCH_ENABLED";

    /** Directory where first-level plugin directories live. Defaults to "./plugins" */
    private final File pluginDirectory;

    /** Runtime catalog of active and quarantined external plugins. */
    private final PluginCatalog pluginCatalog = new PluginCatalog();
    private final ServiceRegistry registry;

    /** In-memory indexes for lazy registration and lookup. */
    private final PluginIndex pluginIndex = new PluginIndex();

    /** Readiness and startup-health views over the plugin catalog. */
    private final PluginReadinessReport readinessReport;

    /** Health checks and quarantine policy for loaded plugins. */
    private final PluginHealthRegistry pluginHealthRegistry;

    /** Core and external service registration/indexing. */
    private final PluginServiceRegistration pluginServiceRegistration;

    /** Background plugin lifecycle state and ordering. */
    private final PluginLifecycle pluginLifecycle = new PluginLifecycle();

    /** Plugin directory watcher start/stop state. */
    private final PluginWatcherControl pluginWatcherControl;

    /** External plugin discovery service. */
    private final PluginDiscoveryService pluginDiscoveryService = new PluginDiscoveryService();

    /** Surface-owned list of plugins that gate readiness. */
    private final RequiredPluginPolicy requiredPluginPolicy = new RequiredPluginPolicy();

    /** Number of DB-function service definitions loaded from external plugin registry files. */
    private int externalRegistryDefinitionCount = 0;

    private final Object pluginLoadMonitor = new Object();

    public PluginManager(ServiceRegistry registry) {
        this(registry, new File("plugins"));
    }

    public PluginManager(ServiceRegistry registry, File pluginDirectory) {
        this.registry = registry;
        this.pluginDirectory = pluginDirectory;
        this.readinessReport = new PluginReadinessReport(
                pluginDirectory,
                pluginCatalog,
                requiredPluginPolicy.pluginIds());
        this.pluginHealthRegistry = new PluginHealthRegistry(pluginCatalog, registry, pluginIndex);
        this.pluginServiceRegistration = new PluginServiceRegistration(registry, pluginIndex);
        this.pluginWatcherControl = new PluginWatcherControl(pluginDirectory, this);
    }

    public void setEngine(Engine engine) {
        this.pluginServiceRegistration.setEngine(engine);
    }

    public File getPluginDirectory() {
        return pluginDirectory;
    }

    public int getExternalRegistryDefinitionCount() {
        return externalRegistryDefinitionCount;
    }

    public void loadPlugins() {
        synchronized (pluginLoadMonitor) {
            externalRegistryDefinitionCount = 0;
            try {
                pluginServiceRegistration.scanLocalServices();
            } catch (Exception e) {
                System.err.printf("Warning: Failed to scan local services: %s%n", e.getMessage());
            }

            loadExternalPlugins();
            pluginLifecycle.instantiateBackgroundPlugins(pluginCatalog.loadedPlugins());
            runDefaultHealthChecks(null, false);
        }
    }

    private void loadExternalPlugins() {
        List<LoadedPlugin> discoveredPlugins = pluginDiscoveryService.discoverPlugins(pluginDirectory);

        for (LoadedPlugin loadedPlugin : discoveredPlugins) {
            if (isPluginKnown(loadedPlugin.getEffectivePluginId())
                    || isPluginKnown(loadedPlugin.getDescriptor().getPluginId())) {
                closeQuietly(loadedPlugin);
                continue;
            }
            markRequiredPlugin(loadedPlugin);
            externalRegistryDefinitionCount += pluginServiceRegistration.registerExternalPlugin(loadedPlugin);
            pluginCatalog.addLoaded(loadedPlugin);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String firstNonBlank(String primary, String secondary) {
        return isBlank(primary) ? secondary : primary;
    }

    public Optional<ServiceCallDefinition> getDefinition(String serviceType) {
        return pluginIndex.getDefinition(serviceType);
    }

    public Collection<ServiceCallDefinition> getDiscoveredDefinitions() {
        return pluginIndex.getDefinitions();
    }

    public void replaceDefinitionFromRegistry(ServiceCallDefinition definition) {
        if (definition == null || isBlank(definition.getServiceType())) {
            return;
        }

        Optional<String> existingHandler =
                pluginIndex.getHandlerClassNameForService(definition.getServiceType());
        if (existingHandler.isPresent()
                && !GenericProcessHandler.class.getName().equals(existingHandler.get())
                && !GenericProcessHandler.class.getSimpleName().equals(existingHandler.get())) {
            return;
        }

        pluginIndex.putDefinition(definition.getServiceType(), definition);

        String handlerClassName = firstNonBlank(definition.getHandlerClass(), definition.getClassName());
        if (!isBlank(handlerClassName)) {
            pluginIndex.putHandlerClassName(definition.getServiceType(), handlerClassName);
        }
    }

    public Optional<String> getHandlerClassNameForService(String serviceType) {
        return pluginIndex.getHandlerClassNameForService(serviceType);
    }

    public Optional<ClassLoader> getClassLoaderFor(String className) {
        return pluginIndex.getClassLoaderFor(className);
    }

    public Optional<Class<?>> findClass(String className) {
        return pluginIndex.findClass(className);
    }

    public Set<String> getLoadedPluginNames() {
        return pluginCatalog.loadedPluginNames();
    }

    public Set<String> getQuarantinedPluginNames() {
        return pluginCatalog.quarantinedPluginNames();
    }

    public Collection<Class<?>> getAllLoadedClasses() {
        return pluginIndex.getAllLoadedClasses();
    }

    public Map<String, String> getLoadedClassSources() {
        return pluginIndex.getClassSources();
    }

    public int getLoadedClassCount() {
        return pluginIndex.getLoadedClassCount();
    }

    public Optional<LoadedPlugin> getLoadedPlugin(String pluginId) {
        return pluginCatalog.findLoaded(pluginId);
    }

    public Optional<LoadedPlugin> getQuarantinedPlugin(String pluginId) {
        return pluginCatalog.findQuarantined(pluginId);
    }

    public Collection<LoadedPlugin> getAllLoadedPlugins() {
        return pluginCatalog.loadedPlugins();
    }

    public Collection<LoadedPlugin> getAllQuarantinedPlugins() {
        return pluginCatalog.quarantinedPlugins();
    }

    public boolean isPluginKnown(String pluginId) {
        return pluginCatalog.isKnown(pluginId);
    }

    public void markRestartRequired(String pluginId, String reason) {
        LoadedPlugin plugin = pluginCatalog.getLoaded(pluginId);
        if (plugin == null) {
            return;
        }

        PluginHealthResult current = plugin.getHealthResult()
                .orElseGet(() -> PluginHealthResult.up(pluginId));
        plugin.setRestartRequired(true);
        plugin.setHealthResult(current.withRestartRequired(
                "plugin-change",
                isBlank(reason) ? "Plugin changed after load; restart required" : reason));
    }

    public JsonObject readinessJson() {
        return readinessReport.readinessJson();
    }

    public boolean areRequiredPlatformPluginsReady() {
        for (String pluginId : requiredPluginPolicy.pluginIds()) {
            LoadedPlugin plugin = pluginCatalog.getLoaded(pluginId);
            if (plugin == null) {
                return false;
            }
            if (plugin.getHealthResult().isPresent() && plugin.getHealthResult().get().isDown()) {
                return false;
            }
        }
        return true;
    }

    public void printStartupHealthSummary() {
        readinessReport.printStartupHealthSummary();
    }

    public void unloadAllPlugins() {
        pluginCatalog.loadedPlugins().forEach(loadedPlugin -> {
            String pluginId = loadedPlugin.getDescriptor().getPluginId();
            try {
                loadedPlugin.getClassLoader().close();
                System.out.println("Unloaded plugin: " + pluginId);
            } catch (IOException e) {
                System.err.printf("Warning: Failed to close class loader for %s: %s%n", pluginId, e.getMessage());
            }
        });

        pluginCatalog.clear();
        pluginIndex.clear();
    }

    public void runDefaultHealthChecks(EnginePluginContext ctx, boolean running) {
        decrementRegistryDefinitions(pluginHealthRegistry.runDefaultHealthChecks(ctx, running));
    }

    public void runBackgroundHealthChecks(EnginePluginContext ctx) {
        decrementRegistryDefinitions(pluginHealthRegistry.runBackgroundHealthChecks(ctx));
    }

    public int loadNewPlugins(EnginePluginContext ctx, boolean running) {
        synchronized (pluginLoadMonitor) {
            int activated = 0;
            List<LoadedPlugin> discoveredPlugins = pluginDiscoveryService.discoverPlugins(pluginDirectory);
            for (LoadedPlugin loadedPlugin : discoveredPlugins) {
                String pluginId = loadedPlugin.getEffectivePluginId();
                String mapKey = loadedPlugin.getDescriptor().getPluginId();
                if (isPluginKnown(pluginId) || isPluginKnown(mapKey)) {
                    closeQuietly(loadedPlugin);
                    continue;
                }

                markRequiredPlugin(loadedPlugin);
                int registeredDefinitions;
                try {
                    registeredDefinitions =
                            pluginServiceRegistration.registerExternalPlugin(loadedPlugin);
                    registry.validateExplicitOverrides();
                } catch (RuntimeException error) {
                    registry.deregisterPlugin(pluginId);
                    loadedPlugin.getRegisteredServiceTypes()
                            .forEach(pluginIndex::removeServiceType);
                    closeQuietly(loadedPlugin);
                    throw error;
                }
                externalRegistryDefinitionCount += registeredDefinitions;
                pluginCatalog.addLoaded(loadedPlugin);
                pluginLifecycle.instantiateBackgroundPlugin(loadedPlugin);

                PluginHealthResult defaultHealth = pluginHealthRegistry.defaultHealth(loadedPlugin, ctx, running);
                loadedPlugin.setHealthResult(defaultHealth);
                if (defaultHealth.isDown()) {
                    quarantinePlugin(loadedPlugin, defaultHealth);
                    continue;
                }

                if (loadedPlugin.getBackgroundPlugin().isPresent()) {
                    try {
                        PluginHealthResult custom = loadedPlugin.getBackgroundPlugin().get().health(ctx).withPluginId(pluginId);
                        PluginHealthResult merged = loadedPlugin.getHealthResult()
                                .map(existing -> existing.merge(custom))
                                .orElse(custom);
                        loadedPlugin.setHealthResult(merged);
                        if (merged.isDown()) {
                            quarantinePlugin(loadedPlugin, merged);
                            continue;
                        }
                        pluginLifecycle.startBackgroundPlugin(loadedPlugin, ctx);
                    } catch (Exception e) {
                        PluginHealthResult failed = PluginHealthResult.down(pluginId, "live-add", e.getMessage());
                        quarantinePlugin(loadedPlugin, failed);
                        continue;
                    }
                }

                activated++;
                System.out.printf("[PluginManager] Live-added plugin: %s%n", pluginId);
            }
            return activated;
        }
    }

    public void startPluginWatcher(EnginePluginContext ctx) {
        pluginWatcherControl.start(ctx);
    }

    public boolean isPluginWatcherActive() {
        return pluginWatcherControl.isActive();
    }

    public void stopPluginWatcher() {
        pluginWatcherControl.stop();
    }

    public void handlePluginRootChange(String pluginId, EnginePluginContext ctx) {
        if (isBlank(pluginId)) {
            loadNewPlugins(ctx, true);
            return;
        }
        if (pluginCatalog.hasLoaded(pluginId)) {
            markRestartRequired(pluginId, "Plugin changed after load; restart required");
            return;
        }
        if (!pluginCatalog.hasQuarantined(pluginId)) {
            loadNewPlugins(ctx, true);
        }
    }

    private void quarantinePlugin(LoadedPlugin plugin, PluginHealthResult result) {
        decrementRegistryDefinitions(pluginHealthRegistry.quarantinePlugin(plugin, result));
    }

    private void decrementRegistryDefinitions(PluginHealthRegistry.RemovedPluginRegistrations removed) {
        if (removed == null) {
            return;
        }
        if (removed.externalDefinitionCount() > 0) {
            externalRegistryDefinitionCount = Math.max(0,
                    externalRegistryDefinitionCount - removed.externalDefinitionCount());
        }
    }

    private void closeQuietly(LoadedPlugin plugin) {
        try {
            plugin.getClassLoader().close();
        } catch (IOException ignored) {
        }
    }

    private void markRequiredPlugin(LoadedPlugin plugin) {
        if (plugin != null && requiredPluginPolicy.isRequired(plugin.getEffectivePluginId())) {
            plugin.setRequiredPlatformPlugin(true);
        }
    }

    public void startBackgroundPlugins(EnginePluginContext ctx) {
        pluginLifecycle.startBackgroundPlugins(pluginCatalog.loadedPlugins(), ctx);
    }

    public void preStartBackgroundPlugins(EnginePluginContext ctx) {
        pluginLifecycle.preStartBackgroundPlugins(pluginCatalog.loadedPlugins(), ctx);
    }

    public void stopBackgroundPlugins() {
        pluginLifecycle.stopBackgroundPlugins();
    }
}
