package com.everyarrow.eventengine.core.services.pipeline;

import com.everyarrow.eventengine.spi.annotations.ServiceCallName;
import com.everyarrow.eventengine.spi.handler.ServiceCallHandler;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceCallName("pipeline.status")
public class PipelineStatusHandler implements ServiceCallHandler {
    private static final Logger logger = LoggerFactory.getLogger(PipelineStatusHandler.class);

    @Override
    public JsonObject execute(JsonObject params, String type, String source) {
        String metadataId = params.has("pipelineMetadataId") ? params.get("pipelineMetadataId").getAsString()
                : "unknown";
        String status = params.has("pipelineStatus") ? params.get("pipelineStatus").getAsString() : "unknown";

        logger.info("Received pipeline status update: metadataId={}, status={}", metadataId, status);

        // TODO: Persist status to database

        JsonObject response = new JsonObject();
        response.addProperty("status", "received");
        return response;
    }
}
