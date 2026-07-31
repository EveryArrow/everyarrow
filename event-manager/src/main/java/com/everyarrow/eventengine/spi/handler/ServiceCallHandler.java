package com.everyarrow.eventengine.spi.handler;

import com.everyarrow.eventengine.core.services.runtime.ServiceCall;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * SPI for all service call handlers.  All handler implementations
 * (legacy, generic, typed) MUST implement this interface so the
 * PluginManager / scanner can discover them uniformly.
 */
public interface ServiceCallHandler {

    /**
     * Execute the handler with the full runtime call available.
     *
     * <p>New handlers should prefer overriding this method when they need
     * source, metadata, or security context. The default preserves the legacy
     * params/type/source contract.</p>
     */
    default JsonElement execute(ServiceCall call) throws Exception {
        JsonObject params = call == null ? null : call.getParams();
        String type = call == null ? null : call.getType();
        String source = call == null || call.getSource() == null ? null : call.getSource().name();
        return execute(params, type, source);
    }

    /**
     * Execute the handler.
     *
     * @param params JSON params object (may be null)
     * @param type   the service call type (e.g. "customer.create")
     * @param source the declared source (e.g. "core" or "plugin:foo.jar")
     * @return JSON payload result to be returned to the caller (JsonObject or JsonArray)
     * @throws Exception on handler failure
     */
    JsonElement execute(JsonObject params, String type, String source) throws Exception;

    /**
     * Optional callback for handler-specific error payloads.
     * Default implementation avoids returning raw exception details to callers.
     */
    default JsonObject onError(ServiceCall call, Exception ex) {
        JsonObject err = new JsonObject();
        err.addProperty("error", "Internal server error");
        return err;
    }
}
