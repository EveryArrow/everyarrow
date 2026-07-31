package com.everyarrow.eventengine.core.services.runtime;

import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.eventengine.spi.annotations.RestRoute;
import com.everyarrow.eventengine.spi.annotations.ServiceCallName;
import com.everyarrow.eventengine.spi.handler.ServiceCallHandler;
import com.google.gson.JsonObject;

/**
 * Example handler showing how a single serviceType ("engine.shutdown")
 * can be exposed via both REST and Webhook routes while still flowing
 * through Engine.execute() and the hook pipeline.
 *
 * The annotated methods act as "entry" pre-processors that can adjust
 * the params before the core execute() logic runs.
 */
@ServiceCallName(value = "engineShutdown", applicationKey = "EVENT_MANAGER")
public class EngineShutdownHandler implements ServiceCallHandler {

    private final Engine engine;

    public EngineShutdownHandler(Engine engine) {
        this.engine = engine;
    }

    @Override
    public JsonObject execute(JsonObject params, String type, String source) {
        JsonObject out = new JsonObject();
        out.addProperty("service", type);
        out.addProperty("status", "shutdown requested");
        out.addProperty("source", source != null ? source : "unknown");

        // Trigger shutdown asynchronously so the HTTP/webhook response can be returned
        Thread t = new Thread(() -> {
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            engine.shutdown();
        }, "engine-shutdown-trigger");
        t.setDaemon(false);
        t.start();

        return out;
    }

    @Override
    public JsonObject onError(ServiceCall call, Exception ex) {
        JsonObject err = new JsonObject();
        err.addProperty("service", call != null ? call.getType() : "unknown");
        err.addProperty("error", ex != null ? ex.getMessage() : "unknown");
        return err;
    }

    // ------------------------------------------------------------------
    // REST and Webhook entry points (A + C model)
    // ------------------------------------------------------------------

    @RestRoute(method = "POST", path = "/engine/shutdown")
    public JsonObject restEntry(JsonObject params) {
        // Decorate params for REST context if desired
        if (params == null) params = new JsonObject();
        params.addProperty("via", "rest");
        return params;
    }
}
