/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.queues;

import java.util.Map;

public interface QueueProducer extends AutoCloseable {
    void send(String destination, byte[] body, Map<String, String> attributes);

    default void sendText(String destination, String text, Map<String, String> attributes) {
        byte[] body = text == null ? new byte[0] : text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        send(destination, body, attributes);
    }
}