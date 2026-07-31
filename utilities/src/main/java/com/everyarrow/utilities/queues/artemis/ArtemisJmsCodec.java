/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.queues.artemis;

import com.everyarrow.utilities.queues.QueueMessage;
import jakarta.jms.BytesMessage;
import jakarta.jms.Message;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public final class ArtemisJmsCodec {

    private ArtemisJmsCodec() {}

    public static QueueMessage fromJms(Message msg) throws Exception {
        byte[] body = readBody(msg);
        Map<String, String> attributes = readAttributes(msg);

        return new QueueMessage(
                msg.getJMSMessageID(),
                msg.getJMSCorrelationID(),
                Instant.now(),
                body,
                attributes
        );
    }

    public static Message toJms(Session session,
                                byte[] body,
                                Map<String, String> attributes,
                                String correlationId) throws Exception {
        String text = (body != null && body.length > 0)
                ? new String(body, StandardCharsets.UTF_8) : "";
        TextMessage out = session.createTextMessage(text);

        if (correlationId != null && !correlationId.trim().isEmpty()) {
            out.setJMSCorrelationID(correlationId);
        }

        if (attributes != null) {
            for (Map.Entry<String, String> e : attributes.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    out.setStringProperty(e.getKey(), e.getValue());
                }
            }
        }

        return out;
    }

    private static byte[] readBody(Message msg) throws Exception {
        if (msg instanceof BytesMessage bm) {
            bm.reset();
            long len = bm.getBodyLength();
            if (len <= 0) return new byte[0];
            if (len > Integer.MAX_VALUE) throw new IllegalStateException("BytesMessage too large: " + len);

            byte[] buf = new byte[(int) len];
            bm.readBytes(buf);
            return buf;
        }

        if (msg instanceof TextMessage tm) {
            String s = tm.getText();
            if (s == null) return new byte[0];
            return s.getBytes(StandardCharsets.UTF_8);
        }

        return new byte[0];
    }

    private static Map<String, String> readAttributes(Message msg) throws Exception {
        Map<String, String> out = new HashMap<>();
        var names = msg.getPropertyNames();
        while (names.hasMoreElements()) {
            Object keyObj = names.nextElement();
            if (keyObj == null) continue;

            String key = String.valueOf(keyObj);
            Object val = msg.getObjectProperty(key);
            if (val != null) out.put(key, String.valueOf(val));
        }
        return out;
    }
}
