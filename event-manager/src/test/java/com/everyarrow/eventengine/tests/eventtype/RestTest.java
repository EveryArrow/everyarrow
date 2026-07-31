package com.everyarrow.eventengine.tests.eventtype;

import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.eventengine.spi.annotations.RestRoute;
import com.everyarrow.eventengine.spi.handler.ServiceCallHandler;
import com.google.gson.JsonObject;

/**
 * Health endpoint exposed via REST.
 *
 * REST: GET /health
 * serviceType = "metadata.health"
 *
 * Engine will route calls to execute().
 */
public class RestTest implements ServiceCallHandler {

    private final Engine engine;

    public RestTest(Engine engine) {
        this.engine = engine;
    }

    /**
     * Unified execution logic used by:
     *  - REST
     *  - internal calls
     *  - monitoring
     *  - hooks
     */
    @Override
    @RestRoute(path = "/tests/restcall", method = "GET")
    public JsonObject execute(JsonObject params, String type, String source) {
        JsonObject result = new JsonObject();
        result.addProperty("status", "OK");
        result.addProperty("engine", "running");
        result.addProperty(
                "pluginIndex",
                engine.getPluginManager() != null ? "loaded" : "none"
        );
        return result;
    }

    @Override
    public JsonObject onError(
            com.everyarrow.eventengine.core.services.runtime.ServiceCall call,
            Exception ex
    ) {
        JsonObject err = new JsonObject();
        err.addProperty("error", ex == null ? "unknown" : ex.getMessage());
        return err;
    }
}
