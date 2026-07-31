/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.plugins;

import com.everyarrow.eventengine.spi.plugin.BackgroundPlugin;
import com.everyarrow.eventengine.spi.plugin.PluginHealthResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Runtime representation of one loaded external plugin.
 *
 * Holds:
 * - plugin descriptor
 * - plugin class loader
 * - discovered concrete handler classes from that artifact
 * - optional plugin registry document loaded from plugin.json
 */
public class LoadedPlugin {

    private final PluginDescriptor descriptor;
    private final PluginClassLoader classLoader;
    private final List<Class<?>> discoveredHandlerClasses = new ArrayList<>();
    private final List<PluginServiceRegistrySource> serviceRegistrySources = new ArrayList<>();
    private PluginRegistryDocument registryDocument;
    private BackgroundPlugin backgroundPlugin;
    private PluginHealthResult healthResult;
    private boolean quarantined;
    private boolean restartRequired;
    private boolean requiredPlatformPlugin;
    private final List<String> registeredServiceTypes = new ArrayList<>();

    public LoadedPlugin(PluginDescriptor descriptor, PluginClassLoader classLoader) {
        this.descriptor = descriptor;
        this.classLoader = classLoader;
    }

    public PluginDescriptor getDescriptor() {
        return descriptor;
    }

    public PluginClassLoader getClassLoader() {
        return classLoader;
    }

    public void addDiscoveredHandlerClass(Class<?> clazz) {
        discoveredHandlerClasses.add(clazz);
    }

    public List<Class<?>> getDiscoveredHandlerClasses() {
        return Collections.unmodifiableList(discoveredHandlerClasses);
    }

    public void addServiceRegistrySource(PluginServiceRegistrySource source) {
        serviceRegistrySources.add(source);
    }

    public List<PluginServiceRegistrySource> getServiceRegistrySources() {
        return Collections.unmodifiableList(serviceRegistrySources);
    }

    public Optional<PluginRegistryDocument> getRegistryDocument() {
        return Optional.ofNullable(registryDocument);
    }

    public Optional<PluginMetadata> getPluginMetadata() {
        return getRegistryDocument().map(PluginRegistryDocument::getPlugin);
    }

    public String getEffectivePluginId() {
        Optional<PluginMetadata> metadata = getPluginMetadata();
        if (metadata.isPresent()) {
            String id = metadata.get().getId();
            if (id != null && !id.isBlank()) {
                return id;
            }
        }
        return descriptor.getPluginId();
    }

    public String getEffectivePluginVersion() {
        return getPluginMetadata()
                .map(PluginMetadata::getVersion)
                .filter(version -> !version.isBlank())
                .orElse(null);
    }

    public void setRegistryDocument(PluginRegistryDocument registryDocument) {
        this.registryDocument = registryDocument;
    }

    public Optional<BackgroundPlugin> getBackgroundPlugin() {
        return Optional.ofNullable(backgroundPlugin);
    }

    public void setBackgroundPlugin(BackgroundPlugin backgroundPlugin) {
        this.backgroundPlugin = backgroundPlugin;
    }

    public void addRegisteredServiceType(String serviceType) {
        if (serviceType != null && !serviceType.isBlank() && !registeredServiceTypes.contains(serviceType)) {
            registeredServiceTypes.add(serviceType);
        }
    }

    public List<String> getRegisteredServiceTypes() {
        return Collections.unmodifiableList(registeredServiceTypes);
    }

    public Optional<PluginHealthResult> getHealthResult() {
        return Optional.ofNullable(healthResult);
    }

    public void setHealthResult(PluginHealthResult healthResult) {
        this.healthResult = healthResult;
    }

    public boolean isQuarantined() {
        return quarantined;
    }

    public void setQuarantined(boolean quarantined) {
        this.quarantined = quarantined;
    }

    public boolean isRestartRequired() {
        return restartRequired;
    }

    public void setRestartRequired(boolean restartRequired) {
        this.restartRequired = restartRequired;
    }

    public boolean isRequiredPlatformPlugin() {
        return requiredPlatformPlugin;
    }

    public void setRequiredPlatformPlugin(boolean requiredPlatformPlugin) {
        this.requiredPlatformPlugin = requiredPlatformPlugin;
    }
}
