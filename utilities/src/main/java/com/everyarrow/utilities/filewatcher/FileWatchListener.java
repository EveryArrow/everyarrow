/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.filewatcher;

/**
 * Listener interface for receiving file events from FileWatcher.
 */
public interface FileWatchListener {
    void onEvent(FileWatchEvent event);
}
