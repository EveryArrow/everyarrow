/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.filewatcher;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * High-level file watcher utility based on WatchService.
 */
public class FileWatcher implements AutoCloseable {

    private final Path directory;
    private final FileWatchListener listener;

    private WatchService watchService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean running = false;

    public FileWatcher(Path directory, FileWatchListener listener) throws IOException {
        this.directory = directory;
        this.listener = listener;

        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
        }
    }

    public void start() throws IOException {
        if (running) return;
        running = true;

        watchService = FileSystems.getDefault().newWatchService();
        directory.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE
        );

        executor.submit(this::processEvents);
    }

    private void processEvents() {
        while (running) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                Path relativePath = (Path) event.context();
                Path fullPath = directory.resolve(relativePath);

                WatchEventType eventType = null;
                if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                    eventType = WatchEventType.CREATE;
                } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                    eventType = WatchEventType.MODIFY;
                } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                    eventType = WatchEventType.DELETE;
                }

                if (eventType != null) {
                    listener.onEvent(new FileWatchEvent(eventType, fullPath));
                }
            }

            boolean valid = key.reset();
            if (!valid) break;
        }
    }

    @Override
    public void close() throws Exception {
        running = false;
        if (watchService != null) {
            watchService.close();
        }
        executor.shutdownNow();
    }
}
