/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.plugins;

import com.everyarrow.utilities.environment.Environment;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Surface-owned declaration of plugins that must be healthy for readiness.
 */
final class RequiredPluginPolicy {

    static final String CONFIG = "EA_REQUIRED_PLUGIN_IDS";

    private final Set<String> pluginIds;

    RequiredPluginPolicy() {
        this(Environment.getInstance().get(CONFIG, ""));
    }

    RequiredPluginPolicy(String configuredPluginIds) {
        LinkedHashSet<String> configured = new LinkedHashSet<>();
        if (configuredPluginIds != null && !configuredPluginIds.isBlank()) {
            Arrays.stream(configuredPluginIds.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .map(PluginPathSecurity::validatePluginId)
                    .forEach(configured::add);
        }
        this.pluginIds = Set.copyOf(configured);
    }

    Set<String> pluginIds() {
        return pluginIds;
    }

    boolean isRequired(String pluginId) {
        return pluginId != null && pluginIds.contains(pluginId);
    }
}
