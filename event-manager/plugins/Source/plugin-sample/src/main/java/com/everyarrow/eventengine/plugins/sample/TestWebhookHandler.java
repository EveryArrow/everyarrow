package com.everyarrow.eventengine.plugins.sample;


import com.everyarrow.eventengine.spi.annotations.ServiceCallName;
import com.everyarrow.eventengine.spi.annotations.Webhook;
import com.everyarrow.eventengine.spi.handler.ServiceCallHandler;
import com.google.gson.JsonObject;

public class TestWebhookHandler implements ServiceCallHandler {

    @Override
    @Webhook(path = "/sample/webhook")
    public JsonObject execute(JsonObject params, String serviceType, String source) {
        JsonObject out = new JsonObject();
        out.addProperty("status", "ok");
        out.addProperty("handler", "sampleWebhook");
        return out;
    }
}
