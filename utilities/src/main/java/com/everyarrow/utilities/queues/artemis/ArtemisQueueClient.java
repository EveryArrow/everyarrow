/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.queues.artemis;

import jakarta.jms.Connection;
import jakarta.jms.Session;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;

public final class ArtemisQueueClient implements AutoCloseable {

    private final ActiveMQConnectionFactory factory;
    private final Connection connection;
    private final Session session;

    public ArtemisQueueClient(ArtemisQueueConfig cfg) {
        try {
            this.factory = new ActiveMQConnectionFactory(cfg.brokerUrl(), cfg.username(), cfg.password());
            this.connection = factory.createConnection();
            this.connection.start();

            // CLIENT_ACKNOWLEDGE so QueueAck controls ack timing
            this.session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize ArtemisQueueClient", e);
        }
    }

    public Session session() {
        return session;
    }

    @Override
    public void close() {
        try { session.close(); } catch (Exception ignored) {}
        try { connection.close(); } catch (Exception ignored) {}
        try { factory.close(); } catch (Exception ignored) {}
    }
}
