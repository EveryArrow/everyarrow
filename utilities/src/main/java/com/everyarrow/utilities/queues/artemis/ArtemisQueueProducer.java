/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.queues.artemis;

import com.everyarrow.utilities.queues.QueueProducer;
import jakarta.jms.Message;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.Session;

import java.util.Map;

public final class ArtemisQueueProducer implements QueueProducer {

    private final ArtemisQueueClient client;

    public ArtemisQueueProducer(ArtemisQueueClient client) {
        this.client = client;
    }

    @Override
    public void send(String destination, byte[] body, Map<String, String> attributes) {
        try {
            Session session = client.session();
            Queue q = session.createQueue(destination);

            try (MessageProducer producer = session.createProducer(q)) {
                Message msg = ArtemisJmsCodec.toJms(session, body, attributes, null);
                producer.send(msg);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to send to destination=" + destination, e);
        }
    }

    @Override
    public void close() {
        // Client lifecycle is owned externally.
    }
}
