/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.queues.artemis;

import com.everyarrow.utilities.queues.QueueAck;
import com.everyarrow.utilities.queues.QueueConsumer;
import com.everyarrow.utilities.queues.QueueMessage;
import com.everyarrow.utilities.queues.QueueMessageHandler;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Queue;
import jakarta.jms.Session;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ArtemisQueueConsumer implements QueueConsumer {

    private static final Logger log = Logger.getLogger(ArtemisQueueConsumer.class.getName());

    private final ArtemisQueueConfig cfg;
    private final ArtemisQueueClient client;
    private final ArtemisQueueProducer producer;
    private final String queueName;
    private final String selector;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread thread;
    private MessageConsumer consumer;

    public ArtemisQueueConsumer(ArtemisQueueConfig cfg,
                                ArtemisQueueClient client,
                                ArtemisQueueProducer producer,
                                String queueName) {
        this(cfg, client, producer, queueName, null);
    }

    public ArtemisQueueConsumer(ArtemisQueueConfig cfg,
                                ArtemisQueueClient client,
                                ArtemisQueueProducer producer,
                                String queueName,
                                String selector) {
        this.cfg = cfg;
        this.client = client;
        this.producer = producer;
        this.queueName = queueName;
        this.selector = trimToNull(selector);
    }

    @Override
    public void start(QueueMessageHandler handler) {
        if (!running.compareAndSet(false, true)) return;

        try {
            Session session = client.session();
            Queue q = session.createQueue(queueName);
            consumer = selector == null ? session.createConsumer(q) : session.createConsumer(q, selector);

            thread = new Thread(() -> loop(handler), "ArtemisQueueConsumer[" + queueName + "]");
            thread.setDaemon(true);
            thread.start();

        } catch (Exception e) {
            running.set(false);
            throw new RuntimeException("Failed to start consumer for queue=" + queueName, e);
        }
    }

    private void loop(QueueMessageHandler handler) {
        while (running.get()) {
            try {
                Message msg = consumer.receive(cfg.receiveTimeoutMs());
                if (msg == null) continue;

                QueueMessage qm = ArtemisJmsCodec.fromJms(msg);
                QueueAck ack = new ArtemisQueueAck(queueName, msg, producer);

                handler.handle(qm, ack);

            } catch (Exception e) {
                log.log(Level.WARNING, "Error in consumer loop. queue=" + queueName, e);
            }
        }
    }

    @Override
    public void stop() {
        running.set(false);

        try { if (thread != null) thread.join(1500); } catch (InterruptedException ignored) {}

        try { if (consumer != null) consumer.close(); } catch (Exception ignored) {}
        consumer = null;
    }

    @Override
    public void close() {
        stop();
        // Client lifecycle is owned externally.
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
