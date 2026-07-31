package com.everyarrow.eventengine.plugins.sample;


import com.everyarrow.eventengine.spi.annotations.ArtemisQueueSubscription;
import com.everyarrow.eventengine.spi.annotations.ServiceCallName;
import com.everyarrow.eventengine.spi.handler.ServiceCallHandler;
import com.google.gson.JsonObject;

public class TestQueueConsumerHandler implements ServiceCallHandler {

    @Override
    @ArtemisQueueSubscription(
            destination = "sample.queue.consumer",
            maxRetries = 3,
            selector = "messageType = 'sample'")
    public JsonObject execute(JsonObject params, String serviceType, String source) {
        JsonObject out = new JsonObject();
        out.addProperty("status", "ok");
        out.addProperty("handler", "sampleQueueConsumer");
        return out;
    }
}
