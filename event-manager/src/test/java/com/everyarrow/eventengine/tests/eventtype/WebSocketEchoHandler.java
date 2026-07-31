package com.everyarrow.eventengine.tests.eventtype;

import com.everyarrow.eventengine.spi.annotations.WebSocketRoute;
import com.everyarrow.eventengine.spi.handler.ServiceCallHandler;
import com.google.gson.JsonObject;

public class WebSocketEchoHandler implements ServiceCallHandler {

    @Override
    @WebSocketRoute(path = "/ws/tests/echo")
    public JsonObject execute(JsonObject params, String type, String source) {
        JsonObject result = new JsonObject();
        result.addProperty("status", "ok");
        result.addProperty("serviceType", type);
        result.addProperty("source", source);
        result.add("echo", params == null ? new JsonObject() : params.deepCopy());
        return result;
    }
}