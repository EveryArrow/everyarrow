/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.queues;

public final class QueueRunner {
    private final QueueConsumer consumer;

    public QueueRunner(QueueConsumer consumer) {
        this.consumer = consumer;
    }

    public void run(QueueMessageHandler handler) {
        consumer.start(handler);
    }

    public void shutdown() {
        consumer.stop();
        try { consumer.close(); } catch (Exception ignored) {}
    }
}