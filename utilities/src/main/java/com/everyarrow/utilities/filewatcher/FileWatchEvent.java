/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.filewatcher;

import java.nio.file.Path;

/**
 * Simple DTO for a file system event.
 */
public class FileWatchEvent {

    private final WatchEventType type;
    private final Path path;

    public FileWatchEvent(WatchEventType type, Path path) {
        this.type = type;
        this.path = path;
    }

    public WatchEventType getType() {
        return type;
    }

    public Path getPath() {
        return path;
    }

    @Override
    public String toString() {
        return "FileWatchEvent{" +
                "type=" + type +
                ", path=" + path +
                '}';
    }
}
