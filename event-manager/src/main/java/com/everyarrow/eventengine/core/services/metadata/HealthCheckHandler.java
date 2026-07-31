package com.everyarrow.eventengine.core.services.metadata;

import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.eventengine.spi.annotations.RestRoute;
import com.everyarrow.eventengine.spi.annotations.Secured;
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
public class HealthCheckHandler implements ServiceCallHandler {

    private final Engine engine;

    public HealthCheckHandler(Engine engine) {
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
    @Secured(isPublic = true)
    @RestRoute(path = "/health", method = "GET")
    public JsonObject execute(JsonObject params, String type, String source) {
        JsonObject result = new JsonObject();
        boolean healthy = engine.isInitialized() && engine.isRunning();
        result.addProperty("status", healthy ? "UP" : "DOWN");
        result.addProperty("engine", healthy ? "running" : "stopped");
        result.addProperty("registryMode", engine.getRegistryRuntimeStatus().mode().name().toLowerCase());
        result.addProperty("registrySource", engine.getRegistryRuntimeStatus().source().name().toLowerCase());
        result.addProperty("registryFallback", engine.getRegistryRuntimeStatus().fallback());
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
