package com.everyarrow.dittoe.pipelines.execution;

import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import com.everyarrow.dittoe.utility.Config;
import com.everyarrow.dittoe.utility.Utils;
import com.everyarrow.dittoe.utility.metadata.PipelineMetadataUtils;
import com.google.gson.JsonObject;
import org.everyarrow.dittoe.schema.BehaviorType;
import org.everyarrow.dittoe.schema.PipelineMetadata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

final class PipelineMetadataLoader {
    private static final DateTimeFormatter PIPELINE_START_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");

    PipelineRuntimeContext loadRuntimeContext(PipelineRunRequest request) throws ExecutionException {
        Path schemaFile = Paths.get(Utils.getPipelinePath(request.pipelineName(), Config.PIPELINE_METADATA_FILE));
        JsonObject schemaJson = loadSchemaJson(schemaFile);
        return new PipelineRuntimeContext(
                request,
                schemaFile,
                schemaJson,
                resolvePipelineStartTime(schemaJson));
    }

    PipelineMetadata loadMetadata(PipelineRuntimeContext context) throws ExecutionException {
        try {
            return PipelineMetadataUtils.deSerializePipelineMetadata(context.schemaJson());
        } catch (Exception e) {
            throw new ExecutionException("Failed to deserialize PMD from: "
                    + context.schemaFile() + ". Error: " + e.getMessage());
        }
    }

    private JsonObject loadSchemaJson(Path schemaFile) throws ExecutionException {
        try {
            String jsonContent = Files.readString(schemaFile);
            return Utils.getGson().fromJson(jsonContent, JsonObject.class);
        } catch (IOException e) {
            throw new ExecutionException("Failed to read schema file: " + schemaFile + ". Error: " + e.getMessage());
        } catch (Exception e) {
            throw new ExecutionException("Failed to parse schema file: " + schemaFile + ". Error: " + e.getMessage());
        }
    }

    private String resolvePipelineStartTime(JsonObject schemaJson) {
        String instrumentationStartTime = extractInstrumentationStartTime(schemaJson);
        if (instrumentationStartTime != null && !instrumentationStartTime.trim().isEmpty()) {
            return instrumentationStartTime;
        }
        return LocalDateTime.now().format(PIPELINE_START_TIME_FORMAT);
    }

    private String extractInstrumentationStartTime(JsonObject json) {
        try {
            if (!json.has("behaviors") || !json.get("behaviors").isJsonArray()) {
                return null;
            }
            for (var behaviorElement : json.getAsJsonArray("behaviors")) {
                if (!behaviorElement.isJsonObject()) {
                    continue;
                }
                JsonObject behavior = behaviorElement.getAsJsonObject();
                if (!behavior.has("behaviorType")
                        || !BehaviorType.Instrumentation.name().equals(behavior.get("behaviorType").getAsString())) {
                    continue;
                }
                if (behavior.has("properties") && behavior.get("properties").isJsonObject()) {
                    JsonObject properties = behavior.getAsJsonObject("properties");
                    if (properties.has("startTime") && !properties.get("startTime").isJsonNull()) {
                        return properties.get("startTime").getAsString();
                    }
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }
}
