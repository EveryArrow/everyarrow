/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.queues;

@FunctionalInterface
public interface QueueMessageHandler {
    void handle(QueueMessage message, QueueAck ack) throws Exception;
}