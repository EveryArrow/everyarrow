/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.queues;

public interface QueueConsumer extends AutoCloseable {
    void start(QueueMessageHandler handler);
    void stop();
}