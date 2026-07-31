/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.queues.artemis;

import com.everyarrow.utilities.queues.DeadLetterDirective;
import com.everyarrow.utilities.queues.QueueAck;
import com.everyarrow.utilities.queues.QueueMessage;
import com.everyarrow.utilities.queues.RetryDirective;
import jakarta.jms.Message;

import java.util.HashMap;
import java.util.Map;

public final class ArtemisQueueAck implements QueueAck {

    private final String baseQueue;
    private final Message original;
    private final ArtemisQueueProducer producer;

    public ArtemisQueueAck(String baseQueue, Message original, ArtemisQueueProducer producer) {
        this.baseQueue = baseQueue;
        this.original = original;
        this.producer = producer;
    }

    @Override
    public void ack() {
        try {
            original.acknowledge();
        } catch (Exception e) {
            throw new RuntimeException("Ack failed", e);
        }
    }

    @Override
    public void retry(RetryDirective directive) {
        try {
            QueueMessage qm = ArtemisJmsCodec.fromJms(original);

            Map<String, String> attrs = new HashMap<>();
            attrs.putAll(qm.attributes() == null ? Map.of() : qm.attributes());
            attrs.put("ea_retry_attempt", String.valueOf(directive.attempt()));
            if (directive.reason() != null) attrs.put("ea_retry_reason", directive.reason());

            producer.send(baseQueue + ".retry", qm.body(), attrs);
            ack();
        } catch (Exception e) {
            throw new RuntimeException("Retry failed", e);
        }
    }

    @Override
    public void deadLetter(DeadLetterDirective directive) {
        try {
            QueueMessage qm = ArtemisJmsCodec.fromJms(original);

            Map<String, String> attrs = new HashMap<>();
            attrs.putAll(qm.attributes() == null ? Map.of() : qm.attributes());
            if (directive.reason() != null) attrs.put("ea_dlq_reason", directive.reason());
            if (directive.errorType() != null) attrs.put("ea_dlq_errorType", directive.errorType());
            if (directive.errorMessage() != null) attrs.put("ea_dlq_errorMessage", directive.errorMessage());

            producer.send(baseQueue + ".dlq", qm.body(), attrs);
            ack();
        } catch (Exception e) {
            throw new RuntimeException("DeadLetter failed", e);
        }
    }
}
