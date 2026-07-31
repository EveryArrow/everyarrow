/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.plugins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Minimal plugin metadata from plugin.json.
 */
public class PluginMetadata {

    private String id;
    private String version;
    private List<String> registryResources = new ArrayList<>();

    /**
     * Fully-qualified class name of the {@code BackgroundPlugin} implementation
     * contributed by this plugin.  Optional — omit for handler-only plugins.
     */
    private String backgroundClass;

    /**
     * Ordered list of plugin IDs that must be started before this plugin.
     * The engine performs a topological sort on this graph before calling onStart.
     */
    private List<String> requires = new ArrayList<>();
    private PluginHealthMetadata health = new PluginHealthMetadata();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public List<String> getRegistryResources() {
        if (registryResources == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(registryResources);
    }

    public void setRegistryResources(List<String> registryResources) {
        this.registryResources = registryResources;
    }

    public String getBackgroundClass() {
        return backgroundClass;
    }

    public void setBackgroundClass(String backgroundClass) {
        this.backgroundClass = backgroundClass;
    }

    public List<String> getRequires() {
        if (requires == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(requires);
    }

    public void setRequires(List<String> requires) {
        this.requires = requires;
    }

    public PluginHealthMetadata getHealth() {
        if (health == null) {
            return new PluginHealthMetadata();
        }
        return health;
    }

    public void setHealth(PluginHealthMetadata health) {
        this.health = health;
    }
}
