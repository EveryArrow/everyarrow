/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.plugins;

import com.everyarrow.eventengine.spi.plugin.EnginePluginContext;

import java.io.File;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Watches the configured plugin root for new plugin folders and first-level
 * plugin artifact changes. This first cut supports live add; modifications to
 * already-loaded plugins are marked restart-required.
 */
public class PluginDirectoryWatcher implements AutoCloseable {

    private static final long DEBOUNCE_MS = 750L;

    private final File pluginRoot;
    private final PluginManager pluginManager;
    private final EnginePluginContext pluginContext;
    private final Map<WatchKey, Path> watchedDirectories = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> pendingScans = new ConcurrentHashMap<>();
    private final ScheduledExecutorService debounceExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> daemonThread(r, "ea-plugin-watch-debounce"));

    private WatchService watchService;
    private Thread watchThread;
    private volatile boolean running;

    public PluginDirectoryWatcher(File pluginRoot, PluginManager pluginManager, EnginePluginContext pluginContext) {
        this.pluginRoot = pluginRoot;
        this.pluginManager = pluginManager;
        this.pluginContext = pluginContext;
    }

    public void start() {
        if (running) {
            return;
        }
        if (pluginRoot == null || !pluginRoot.isDirectory()) {
            System.out.println("[PluginDirectoryWatcher] Plugin root does not exist; watcher disabled: " + pluginRoot);
            return;
        }

        try {
            watchService = pluginRoot.toPath().getFileSystem().newWatchService();
            registerDirectory(pluginRoot.toPath());
            registerExistingPluginDirectories();
            running = true;
            watchThread = daemonThread(this::watchLoop, "ea-plugin-watch");
            watchThread.start();
            System.out.println("[PluginDirectoryWatcher] Watching plugin root: " + pluginRoot.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("[PluginDirectoryWatcher] Failed to start: " + e.getMessage());
        }
    }

    private static Thread daemonThread(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    private void registerExistingPluginDirectories() throws IOException {
        try (var paths = Files.list(pluginRoot.toPath())) {
            paths.filter(Files::isDirectory)
                    .forEach(path -> {
                        try {
                            registerDirectory(path);
                        } catch (IOException e) {
                            System.err.println("[PluginDirectoryWatcher] Failed to watch " + path + ": " + e.getMessage());
                        }
                    });
        }
    }

    private void registerDirectory(Path directory) throws IOException {
        WatchKey key = directory.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
        watchedDirectories.put(key, directory);
    }

    private void watchLoop() {
        while (running) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (ClosedWatchServiceException closed) {
                return;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }

            Path watchedDirectory = watchedDirectories.get(key);
            if (watchedDirectory != null) {
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        scheduleScan(null);
                        continue;
                    }
                    handleEvent(watchedDirectory, event);
                }
            }

            if (!key.reset()) {
                watchedDirectories.remove(key);
            }
        }
    }

    private void handleEvent(Path watchedDirectory, WatchEvent<?> event) {
        Object context = event.context();
        if (!(context instanceof Path relativePath)) {
            scheduleScan(null);
            return;
        }

        Path changedPath = watchedDirectory.resolve(relativePath);
        if (watchedDirectory.equals(pluginRoot.toPath())) {
            String pluginId = relativePath.getFileName().toString();
            if (Files.isDirectory(changedPath)) {
                try {
                    registerDirectory(changedPath);
                } catch (IOException e) {
                    System.err.println("[PluginDirectoryWatcher] Failed to watch new plugin directory "
                            + changedPath + ": " + e.getMessage());
                }
            }
            scheduleScan(pluginId);
            return;
        }

        String pluginId = watchedDirectory.getFileName().toString();
        scheduleScan(pluginId);
    }

    private void scheduleScan(String pluginId) {
        String key = pluginId == null || pluginId.isBlank() ? "*" : pluginId;
        ScheduledFuture<?> previous = pendingScans.remove(key);
        if (previous != null) {
            previous.cancel(false);
        }

        ScheduledFuture<?> next = debounceExecutor.schedule(() -> {
            pendingScans.remove(key);
            pluginManager.handlePluginRootChange("*".equals(key) ? null : key, pluginContext);
        }, DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        pendingScans.put(key, next);
    }

    @Override
    public void close() {
        running = false;
        for (ScheduledFuture<?> future : pendingScans.values()) {
            future.cancel(false);
        }
        pendingScans.clear();
        debounceExecutor.shutdownNow();
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {
            }
        }
        if (watchThread != null) {
            watchThread.interrupt();
        }
    }
}
