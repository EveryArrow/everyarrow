/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.plugins;

import com.everyarrow.eventengine.spi.plugin.EnginePluginContext;
import com.everyarrow.utilities.environment.Environment;

import java.io.File;

/**
 * Start/stop state and enablement policy for the plugin directory watcher.
 */
final class PluginWatcherControl {

    private final File pluginDirectory;
    private final PluginManager pluginManager;
    private PluginDirectoryWatcher pluginDirectoryWatcher;

    PluginWatcherControl(File pluginDirectory, PluginManager pluginManager) {
        this.pluginDirectory = pluginDirectory;
        this.pluginManager = pluginManager;
    }

    void start(EnginePluginContext ctx) {
        if (pluginDirectoryWatcher != null) {
            return;
        }
        if (!isPluginWatcherEnabled()) {
            return;
        }
        pluginDirectoryWatcher = new PluginDirectoryWatcher(pluginDirectory, pluginManager, ctx);
        pluginDirectoryWatcher.start();
    }

    boolean isActive() {
        return pluginDirectoryWatcher != null;
    }

    void stop() {
        if (pluginDirectoryWatcher == null) {
            return;
        }
        pluginDirectoryWatcher.close();
        pluginDirectoryWatcher = null;
    }

    private boolean isPluginWatcherEnabled() {
        String configured = System.getProperty(PluginManager.PLUGIN_WATCH_ENABLED_CONFIG);
        if (isBlank(configured)) {
            configured = Environment.getInstance().get(PluginManager.PLUGIN_WATCH_ENABLED_CONFIG, "false");
        }
        return Boolean.parseBoolean(configured);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
