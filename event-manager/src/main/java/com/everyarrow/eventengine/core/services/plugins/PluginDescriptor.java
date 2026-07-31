/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.plugins;

import java.io.File;

/**
 * Lightweight operational identity for one plugin directory.
 *
 * This phase uses the first-level plugin directory name as the descriptor id.
 * Later this can evolve to use explicit metadata from plugin.json.
 */
public class PluginDescriptor {

    private final String pluginId;
    private final File pluginDirectory;

    public PluginDescriptor(String pluginId, File pluginDirectory) {
        this.pluginId = pluginId;
        this.pluginDirectory = pluginDirectory;
    }

    public String getPluginId() {
        return pluginId;
    }

    public File getPluginDirectory() {
        return pluginDirectory;
    }

    public File getJarFile() {
        return pluginDirectory;
    }
}
