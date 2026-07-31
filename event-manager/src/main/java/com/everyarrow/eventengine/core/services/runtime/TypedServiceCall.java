package com.everyarrow.eventengine.core.services.runtime;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * Strongly-typed wrapper around a ServiceCall.
 *
 * Subclasses define:
 *     record Input(...) {}
 *     record Output(...)
 *
 * And implement:
 *     Output executeTyped(Input input)
 */
public abstract class TypedServiceCall<I, O> {

    private static final Gson gson = new Gson();

    /** Converts raw JSON params → typed Input */
    public final I parseInput(JsonObject params, Class<I> inputClass) {
        if (params == null) return null;
        return gson.fromJson(params, inputClass);
    }

    /** Converts typed Output → JSON */
    public final JsonObject toJson(O output) {
        return gson.toJsonTree(output).getAsJsonObject();
    }

    /** Subclasses must implement the type-safe handler */
    protected abstract O executeTyped(I input) throws Exception;

    /**
     * Entry point called by GenericProcessHandler or any ServiceCallHandler adapter.
     */
    public final JsonObject execute(JsonObject params, Class<I> inputClass) throws Exception {
        I input = parseInput(params, inputClass);
        O output = executeTyped(input);
        return toJson(output);
    }
}
