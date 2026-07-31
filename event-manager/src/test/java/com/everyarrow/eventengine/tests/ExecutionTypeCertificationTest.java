package com.everyarrow.eventengine.tests;

import com.everyarrow.eventengine.tests.eventtype.QueueHelloWorldService;
import com.everyarrow.utilities.environment.Environment;
import com.everyarrow.utilities.queues.QueueAck;
import com.everyarrow.utilities.queues.QueueMessage;
import com.everyarrow.utilities.queues.QueueMessageHandler;
import com.everyarrow.utilities.queues.artemis.ArtemisQueueClient;
import com.everyarrow.utilities.queues.artemis.ArtemisQueueConfig;
import com.everyarrow.utilities.queues.artemis.ArtemisQueueConsumer;
import com.everyarrow.utilities.queues.artemis.ArtemisQueueProducer;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExecutionTypeCertificationTest {

    enum AdapterKind {
        REST,
        WEBHOOK,
        CALENDAR,
        DB_GENERIC,
        QUEUE
    }

    private EngineHarness harness;

    @BeforeAll
    void bootOnce() throws Exception {
        harness = EngineHarness.start();
    }

    @AfterAll
    void shutdownOnce() {
        if (harness != null) {
            harness.close();
        }
    }

@org.junit.jupiter.api.Test
void certifyRest_executionType() throws Exception {
    certifyRest();
}

@org.junit.jupiter.api.Test
void certifyWebhook_executionType() throws Exception {
    certifyWebhook();
}

@org.junit.jupiter.api.Test
void certifyCalendar_executionType() throws Exception {
    certifyCalendar();
}

@org.junit.jupiter.api.Test
void certifyDbGeneric_executionType() throws Exception {
    certifyDbGeneric();
}

@org.junit.jupiter.api.Test
void certifyQueue_executionType() throws Exception {
    certifyQueue();
}


    // -------------------------
    // QUEUE certification
    // -------------------------

    private void certifyQueue() throws Exception {        Environment env = Environment.getInstance();

        String destination = "ea.tests.queue";
        String serviceType = "queueHelloWorldService"; // must match registry key for QueueHelloWorldService
        String correlationId = UUID.randomUUID().toString();

        QueueHelloWorldService.resetForCorrelationId(correlationId);

        // Publish canonical envelope
        JsonObject params = new JsonObject();
        params.addProperty("correlationId", correlationId);
        params.addProperty("message", "certification");

        JsonObject envelope = new JsonObject();
        envelope.addProperty("v", 1);
        envelope.addProperty("serviceType", serviceType);
        envelope.add("params", params);

        publishJsonToQueue(env, destination, envelope.toString(), correlationId);

        boolean ran = QueueHelloWorldService.awaitRan(3000);
        assertTrue(ran, "Queue handler did not run within timeout correlationId=" + correlationId);
        assertTrue(QueueHelloWorldService.didRun(), "Queue handler did not set ran=true correlationId=" + correlationId);

        JsonObject lastParams = QueueHelloWorldService.getLastParams();
        assertNotNull(lastParams, "Expected lastParams to be set");
        assertEquals(correlationId, lastParams.get("correlationId").getAsString(),
                "Handler received unexpected correlationId");

        JsonObject lastResult = QueueHelloWorldService.getLastResult();
        assertNotNull(lastResult, "Expected lastResult to be set");
        assertEquals("Hello World", lastResult.get("message").getAsString(),
                "Expected handler result message to be 'Hello World'");

        // Assert message was acked/removed: queue should be empty after processing.
        assertQueueEmpty(env, destination, 350);
    }

    private void publishJsonToQueue(Environment env, String destination, String json, String correlationId) throws Exception {
        String brokerUrl = env.require("JMS_BROKER_URL");
        String user = env.get("JMS_USERNAME", "");
        String pass = env.get("JMS_PASSWORD", "");

        ArtemisQueueConfig cfg = new ArtemisQueueConfig(
                brokerUrl,
                blankToNull(user),
                blankToNull(pass),
                0
        );

        ArtemisQueueClient client = new ArtemisQueueClient(cfg);
        ArtemisQueueProducer producer = new ArtemisQueueProducer(client);

        try {
            Map<String, String> attrs = new HashMap<>();
            attrs.put("contentType", "application/json");
            attrs.put("encoding", "utf-8");
            attrs.put("correlationId", correlationId);

            producer.send(destination, json.getBytes(StandardCharsets.UTF_8), attrs);
        } finally {
            try { producer.close(); } catch (Exception ignored) {}
            try { client.close(); } catch (Exception ignored) {}
        }
    }

    /**
     * Certification-grade ack/removal check:
     * After handler ran, we should not be able to observe any additional message
     * in a short drain window.
     *
     * This uses Utilities ArtemisQueueConsumer with a short receive timeout.
     */
    private void assertQueueEmpty(Environment env, String destination, long drainWindowMs) throws Exception {
        String brokerUrl = env.require("JMS_BROKER_URL");
        String user = env.get("JMS_USERNAME", "");
        String pass = env.get("JMS_PASSWORD", "");

        // Short receive timeout to avoid long waits
        ArtemisQueueConfig cfg = new ArtemisQueueConfig(
                brokerUrl,
                blankToNull(user),
                blankToNull(pass),
                50
        );

        ArtemisQueueClient client = new ArtemisQueueClient(cfg);
        ArtemisQueueProducer producer = new ArtemisQueueProducer(client);
        ArtemisQueueConsumer consumer = new ArtemisQueueConsumer(cfg, client, producer, destination);

        final CountDownLatch sawMessage = new CountDownLatch(1);

        QueueMessageHandler handler = new QueueMessageHandler() {
            @Override
            public void handle(QueueMessage msg, QueueAck ack) {
                try {
                    // If we see anything here, the queue was not empty.
                    sawMessage.countDown();
                } finally {
                    // Ack to avoid poisoning the queue during certification check
                    try { ack.ack(); } catch (Exception ignored) {}
                }
            }
        };

        try {
            consumer.start(handler);

            boolean received = sawMessage.await(drainWindowMs, TimeUnit.MILLISECONDS);
            assertFalse(received, "Queue was not empty after processing. destination=" + destination);

        } finally {
            try { consumer.stop(); } catch (Exception ignored) {}
            try { consumer.close(); } catch (Exception ignored) {}
            try { producer.close(); } catch (Exception ignored) {}
            try { client.close(); } catch (Exception ignored) {}
        }
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    // -------------------------
    // Existing certifiers
    // -------------------------
    // Keep your existing certifyRest/certifyWebhook/certifyCalendar/certifyDbGeneric here unchanged.
    // I’m not reprinting them to avoid accidental drift.
    private void certifyRest() throws Exception { /* existing */ }
    private void certifyWebhook() throws Exception { /* existing */ }
    private void certifyCalendar() throws Exception { /* existing */ }
    private void certifyDbGeneric() throws Exception { /* existing */ }
}
