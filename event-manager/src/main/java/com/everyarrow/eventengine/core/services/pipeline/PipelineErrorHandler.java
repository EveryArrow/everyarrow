package com.everyarrow.eventengine.core.services.pipeline;

import com.everyarrow.eventengine.spi.annotations.ServiceCallName;
import com.everyarrow.eventengine.spi.handler.ServiceCallHandler;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceCallName("pipeline.error_count")
public class PipelineErrorHandler implements ServiceCallHandler {
    private static final Logger logger = LoggerFactory.getLogger(PipelineErrorHandler.class);

    @Override
    public JsonObject execute(JsonObject params, String type, String source) {
        String metadataId = params.has("pipelineMetadataId") ? params.get("pipelineMetadataId").getAsString()
                : "unknown";
        int errorCount = params.has("errorCount") ? params.get("errorCount").getAsInt() : -1;

        logger.info("Received pipeline error count: metadataId={}, count={}", metadataId, errorCount);

        // TODO: Persist error count to database

        JsonObject response = new JsonObject();
        response.addProperty("status", "received");
        return response;
    }
}
