package com.everyarrow.eventengine.core.services.pipeline;

import com.everyarrow.eventengine.spi.annotations.ServiceCallName;
import com.everyarrow.eventengine.spi.handler.ServiceCallHandler;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceCallName("pipeline.heartbeat")
public class PipelineHeartbeatHandler implements ServiceCallHandler {
    private static final Logger logger = LoggerFactory.getLogger(PipelineHeartbeatHandler.class);

    @Override
    public JsonObject execute(JsonObject params, String type, String source) {
        String metadataId = params.has("pipelineMetadataId") ? params.get("pipelineMetadataId").getAsString()
                : "unknown";
        String instanceId = params.has("instanceId") ? params.get("instanceId").getAsString() : "unknown";

        logger.info("Received pipeline heartbeat: metadataId={}, instanceId={}", metadataId, instanceId);

        // TODO: Persist heartbeat to database

        JsonObject response = new JsonObject();
        response.addProperty("status", "received");
        return response;
    }
}
