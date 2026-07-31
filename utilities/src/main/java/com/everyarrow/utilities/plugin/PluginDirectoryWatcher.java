/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.plugin;

import com.everyarrow.utilities.filewatcher.FileWatcher;
import com.everyarrow.utilities.filewatcher.FileWatchEvent;
import com.everyarrow.utilities.filewatcher.WatchEventType;

import java.nio.file.Path;

/**
 * Bridges the generic FileWatcher utility to plugin registry actions.
 * Engines can use PluginManager instead for higher-level control,
 * but this remains as a simple helper if needed.
 *
 * Plugin directories are a trusted-code boundary. They must be writable only by
 * administrators or the process owner responsible for deploying plugins.
 */
public class PluginDirectoryWatcher {

    private final FileWatcher watcher;

    public PluginDirectoryWatcher(Path directory, PluginRegistry registry, ClassLoader parent) throws Exception {
        this.watcher = new FileWatcher(directory, this::onEvent);
        this.registry = registry;
        this.parent = parent;
    }

    private final PluginRegistry registry;
    private final ClassLoader parent;

    private void onEvent(FileWatchEvent event) {
        try {
            Path p = event.getPath();
            if (!p.toString().endsWith(".jar")) return;

            String id = p.getFileName().toString();

            if (event.getType() == WatchEventType.CREATE || event.getType() == WatchEventType.MODIFY) {
                PluginClassLoader loader = new PluginClassLoader(new PluginDescriptor(id, p), parent);
                registry.register(loader);
            } else if (event.getType() == WatchEventType.DELETE) {
                registry.unregister(id);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to process plugin event for " + event.getPath(), e);
        }
    }

    public void start() throws Exception {
        watcher.start();
    }

    public void stop() throws Exception {
        watcher.close();
    }
}
