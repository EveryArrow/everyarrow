package com.everyarrow.eventengine.core.services.metadata;

import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.eventengine.core.services.runtime.EventManagerServiceIdentity;
import com.everyarrow.eventengine.spi.annotations.RestRoute;
import com.everyarrow.eventengine.spi.annotations.Secured;
import com.everyarrow.eventengine.spi.annotations.ServiceCallName;
import com.everyarrow.eventengine.spi.handler.ServiceCallHandler;
import com.google.gson.JsonObject;

/**
 * Readiness endpoint for runtime/plugin health.
 *
 * REST: GET /ready
 */
public class ReadyCheckHandler implements ServiceCallHandler {

    private final Engine engine;

    public ReadyCheckHandler(Engine engine) {
        this.engine = engine;
    }

    @Override
    public JsonObject execute(JsonObject params, String type, String source) {
        return publicReady(params, type, source);
    }

    @ServiceCallName(value = "readyCheck", applicationKey = "EVENT_MANAGER", open = true)
    @Secured(isPublic = true)
    @RestRoute(path = "/ready", method = "GET")
    public JsonObject publicReady(JsonObject params, String type, String source) {
        JsonObject result = new JsonObject();
        boolean ready = engine.isReady();
        result.addProperty("status", ready ? "UP" : "STARTING");
        result.addProperty("engine", "running");
        result.addProperty("ready", ready);
        return result;
    }

    @ServiceCallName(value = "readyDetails", applicationKey = "EVENT_MANAGER")
    @Secured(roles = {"system", "admin"}, scopes = {"core.read"})
    @RestRoute(path = "/core/ready", method = "GET")
    public JsonObject detailedReady(JsonObject params, String type, String source) {
        JsonObject result = new JsonObject();
        boolean ready = engine.isReady();
        result.addProperty("status", ready ? "UP" : "STARTING");
        result.addProperty("engine", "running");
        result.addProperty("initialized", engine.isInitialized());
        result.addProperty("running", engine.isRunning());
        result.addProperty("ready", ready);
        boolean requiredPluginsReady = engine.getPluginManager().areRequiredPlatformPluginsReady();
        result.addProperty("requiredPluginsReady", requiredPluginsReady);
        result.addProperty("requiredPlatformPluginsReady", requiredPluginsReady);
        result.add("registry", engine.getRegistryRuntimeStatus().toJson(true));
        result.add("serviceIdentity", new EventManagerServiceIdentity().readinessJson());
        result.add("plugins", engine.getPluginManager().readinessJson());
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
