package com.everyarrow.eventengine.spi.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class ServiceCallResponse {

    private String serviceType;
    private JsonElement body;
    private long durationMs;
    private int httpStatus = 200;

    private ServiceCallResponse() {}

    public static ServiceCallResponse ok(String serviceType,
                                         JsonElement body,
                                         long durationMs) {

        ServiceCallResponse r = new ServiceCallResponse();
        r.serviceType = serviceType;
        r.body = body;
        r.durationMs = durationMs;
        return r;
    }

    public static ServiceCallResponse ok(String message) {
        JsonObject body = new JsonObject();
        body.addProperty("message", message);
        return ok("ok", body, 0L);
    }

    public ServiceCallResponse withHttpStatus(int httpStatus) {
        this.httpStatus = httpStatus;
        return this;
    }

    public String getServiceType() { return serviceType; }
    public JsonElement getBody() { return body; }
    public JsonObject getBodyAsObject() { return body instanceof JsonObject jo ? jo : null; }
    public long getDurationMs() { return durationMs; }
    public int getHttpStatus() { return httpStatus; }

    public String toJson() {
        JsonObject root = new JsonObject();
        root.add("body", body != null ? body : new JsonObject());
        root.addProperty("serviceType", serviceType);
        root.addProperty("durationMs", durationMs);
        root.addProperty("status", httpStatus);
        return root.toString();
    }
}
