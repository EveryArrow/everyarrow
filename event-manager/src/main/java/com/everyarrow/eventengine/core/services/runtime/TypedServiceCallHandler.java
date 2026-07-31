package com.everyarrow.eventengine.core.services.runtime;

import com.everyarrow.eventengine.spi.handler.ServiceCallHandler;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * Base class for typed handlers.  Subclasses declare a typed Input and Output (records)
 * and implement executeTyped(Input) returning Output. This adapter performs:
 *
 *   JsonObject -> Input (Gson) -> executeTyped -> Output -> JsonObject (Gson)
 *
 * and therefore implements the runtime SPI so the scanner finds it.
 */
public abstract class TypedServiceCallHandler<I, O> implements ServiceCallHandler {

    private static final Gson GSON = new Gson();
    private final Class<I> inputClass;

    protected TypedServiceCallHandler(Class<I> inputClass) {
        this.inputClass = inputClass;
    }

    /**
     * Implement business logic in a strongly typed fashion.
     */
    protected abstract O executeTyped(I input) throws Exception;

    @Override
    public JsonObject execute(JsonObject params, String type, String source) throws Exception {
        I input = null;
        if (params != null && inputClass != null) {
            input = GSON.fromJson(params, inputClass);
        }
        O out = executeTyped(input);
        if (out == null) return new JsonObject();
        return GSON.toJsonTree(out).getAsJsonObject();
    }

    @Override
    public JsonObject onError(ServiceCall call, Exception ex) {
        JsonObject err = new JsonObject();
        err.addProperty("error", "Internal server error");
        return err;
    }
}
