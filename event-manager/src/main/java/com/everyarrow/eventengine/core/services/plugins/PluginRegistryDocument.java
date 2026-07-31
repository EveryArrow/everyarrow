/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.plugins;

import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * Plugin registry document loaded from META-INF/everyarrow/plugin.json.
 *
 * Important:
 * - plugin metadata is plugin-specific
 * - services reuse the engine's existing ServiceCallDefinition model
 */
public class PluginRegistryDocument {

    private PluginMetadata plugin;
    private List<ServiceCallDefinition> services = new ArrayList<>();

    public PluginMetadata getPlugin() {
        return plugin;
    }

    public void setPlugin(PluginMetadata plugin) {
        this.plugin = plugin;
    }

    public List<ServiceCallDefinition> getServices() {
        return services;
    }

    public void setServices(List<ServiceCallDefinition> services) {
        this.services = services;
    }
}