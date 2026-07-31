package com.everyarrow.eventengine.core.services.metadata;

import com.everyarrow.eventengine.core.services.plugins.PluginManager;
import com.everyarrow.eventengine.spi.handler.ServiceCallHandler;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class ActivePluginsHandler implements ServiceCallHandler {

    private final PluginManager pluginManager;

    public ActivePluginsHandler(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    @Override
    public JsonObject execute(com.google.gson.JsonObject params, String type, String source) {
        JsonObject out = new JsonObject();
        JsonArray arr = new JsonArray();
        for (String name : pluginManager.getLoadedPluginNames()) {
            arr.add(name);
        }
        out.add("activePlugins", arr);
        out.addProperty("count", arr.size());
        out.add("health", pluginManager.readinessJson().get("pluginHealth"));
        return out;
    }

    @Override
    public JsonObject onError(com.everyarrow.eventengine.core.services.runtime.ServiceCall call, Exception ex) {
        JsonObject err = new JsonObject();
        err.addProperty("error", ex == null ? "unknown" : ex.getMessage());
        return err;
    }
}
