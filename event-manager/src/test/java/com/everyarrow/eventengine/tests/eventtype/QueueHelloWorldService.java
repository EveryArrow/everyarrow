package com.everyarrow.eventengine.tests.eventtype;

import com.everyarrow.eventengine.spi.annotations.ArtemisQueueSubscription;
import com.everyarrow.eventengine.spi.handler.ServiceCallHandler;
import com.google.gson.JsonObject;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class QueueHelloWorldService implements ServiceCallHandler {

    private static volatile String expectedCorrelationId;
    private static volatile CountDownLatch latch = new CountDownLatch(1);

    private static volatile boolean ran = false;
    private static volatile JsonObject lastParams;
    private static volatile JsonObject lastResult;

    public static synchronized void resetForCorrelationId(String correlationId) {
        expectedCorrelationId = correlationId;
        ran = false;
        lastParams = null;
        lastResult = null;
        latch = new CountDownLatch(1);
    }

    public static boolean awaitRan(long timeoutMs) throws InterruptedException {
        return latch.await(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public static boolean didRun() {
        return ran;
    }

    public static JsonObject getLastParams() {
        return lastParams;
    }

    public static JsonObject getLastResult() {
        return lastResult;
    }

    // Marker method for subscription discovery via annotations.
    // maxRetries=0 makes certification deterministic: either it runs once or fails.
@Override
    @ArtemisQueueSubscription(destination = "ea.tests.queue", maxRetries = 0)
    public JsonObject execute(JsonObject params, String type, String source) {
        lastParams = params;

        String incomingCorrelationId = null;
        if (params != null && params.has("correlationId") && !params.get("correlationId").isJsonNull()) {
            incomingCorrelationId = params.get("correlationId").getAsString();
        }

        // Determinism: only count this run if correlationId matches what the test published.
        if (expectedCorrelationId != null && !expectedCorrelationId.equals(incomingCorrelationId)) {
            return new JsonObject();
        }

        ran = true;

        JsonObject out = new JsonObject();
        out.addProperty("message", "Hello World");
        lastResult = out;

        latch.countDown();
        return out;
    }
}
