package com.everyarrow.eventengine.core.services.metadata;

import com.everyarrow.eventengine.core.services.registry.ServiceRegistry;
import com.everyarrow.eventengine.core.services.registry.FunctionResolution;
import com.everyarrow.eventengine.spi.handler.ServiceCallHandler;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class RegistryListHandler implements ServiceCallHandler {

    private final ServiceRegistry registry;

    public RegistryListHandler(ServiceRegistry registry) {
        this.registry = registry;
    }

    @Override
    public JsonObject execute(JsonObject params, String type, String source) {
        JsonObject out = new JsonObject();
        JsonArray arr = new JsonArray();
        for (String s : registry.listServiceTypes()) {
            arr.add(s);
        }
        out.add("serviceTypes", arr);
        out.addProperty("count", arr.size());

        JsonArray resolutions = new JsonArray();
        String requestedServiceType = requestedServiceType(params);
        if (requestedServiceType == null) {
            for (FunctionResolution resolution : registry.listResolutions()) {
                resolutions.add(resolution.toJson());
            }
        } else {
            FunctionResolution resolution = registry.getResolution(requestedServiceType);
            if (resolution != null) {
                resolutions.add(resolution.toJson());
            }
        }
        out.add("resolutions", resolutions);
        return out;
    }

    private String requestedServiceType(JsonObject params) {
        if (params == null
                || !params.has("serviceType")
                || params.get("serviceType").isJsonNull()) {
            return null;
        }
        String serviceType = params.get("serviceType").getAsString();
        return serviceType == null || serviceType.isBlank()
                ? null
                : serviceType.trim();
    }

    @Override
    public JsonObject onError(com.everyarrow.eventengine.core.services.runtime.ServiceCall call, Exception ex) {
        JsonObject err = new JsonObject();
        err.addProperty("error", ex == null ? "unknown" : ex.getMessage());
        return err;
    }
}
