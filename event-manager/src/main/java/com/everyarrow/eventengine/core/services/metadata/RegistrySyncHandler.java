package com.everyarrow.eventengine.core.services.metadata;

import com.everyarrow.eventengine.core.services.plugins.PluginManager;
import com.everyarrow.eventengine.spi.handler.ServiceCallHandler;
import com.google.gson.JsonObject;

public class RegistrySyncHandler implements ServiceCallHandler {

    private final PluginManager pluginManager;

    public RegistrySyncHandler(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    @Override
    public JsonObject execute(JsonObject params, String type, String source) throws Exception {
        pluginManager.loadNewPlugins(null, true);
        JsonObject out = new JsonObject();
        out.addProperty("status", "ok");
        out.addProperty("message", "Plugin live-add scan triggered");
        return out;
    }

    @Override
    public JsonObject onError(com.everyarrow.eventengine.core.services.runtime.ServiceCall call, Exception ex) {
        JsonObject err = new JsonObject();
        err.addProperty("error", ex == null ? "unknown" : ex.getMessage());
        return err;
    }
}
