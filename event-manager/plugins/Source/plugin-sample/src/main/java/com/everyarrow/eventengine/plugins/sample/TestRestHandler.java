package com.everyarrow.eventengine.plugins.sample;


import com.everyarrow.eventengine.spi.annotations.RestRoute;
import com.everyarrow.eventengine.spi.annotations.ServiceCallName;
import com.everyarrow.eventengine.spi.handler.ServiceCallHandler;
import com.google.gson.JsonObject;

public class TestRestHandler implements ServiceCallHandler {

    @Override
    @RestRoute(path = "/sample/rest", method = "GET")
    public JsonObject execute(JsonObject params, String serviceType, String source) {
        JsonObject out = new JsonObject();
        out.addProperty("status", "ok");
        out.addProperty("handler", "sampleRest");
        return out;
    }
}