package com.everyarrow.eventengine.core.services.pipeline;

import com.everyarrow.eventengine.spi.annotations.ServiceCallName;
import com.everyarrow.eventengine.spi.handler.ServiceCallHandler;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceCallName("pipeline.log")
public class PipelineLogHandler implements ServiceCallHandler {
    private static final Logger logger = LoggerFactory.getLogger(PipelineLogHandler.class);

    @Override
    public JsonObject execute(JsonObject params, String type, String source) {
        String metadataId = params.has("pipelineMetadataId") ? params.get("pipelineMetadataId").getAsString()
                : "unknown";
        String executionId = params.has("pipelineExecutionId") ? params.get("pipelineExecutionId").getAsString()
                : "unknown";

        int logCount = 0;
        if (params.has("logs")) {
            JsonArray logs = params.getAsJsonArray("logs");
            logCount = logs.size();
            // Optional: iterate and log individual lines if needed, or batch process
        }

        logger.info("Received pipeline logs: metadataId={}, executionId={}, count={}", metadataId, executionId,
                logCount);

        // TODO: Persist logs to database or log aggregation service

        JsonObject response = new JsonObject();
        response.addProperty("status", "received");
        return response;
    }
}
