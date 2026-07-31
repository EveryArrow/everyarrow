package com.everyarrow.dittoe.utility;

import com.everyarrow.dittoe.utility.metadata.PipelineMetadataUtils;
import com.everyarrow.dittoe.utility.security.SecretRedactor;
import com.google.gson.JsonObject;
import org.everyarrow.dittoe.schema.BehaviorType;
import org.everyarrow.dittoe.schema.PipelineMetadata;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.everyarrow.utilities.property.PropertyManagement.validateProperties;

final class PipelineMetadataRuntime {
    private PipelineMetadataRuntime() {
    }

    static boolean isFirstMicroBatch(PipelineMetadata pipelineMetadata) {
        return pipelineMetadata.getBehaviors().stream()
                .filter(behavior -> behavior.getBehaviorType() == BehaviorType.MicroBatch)
                .findFirst()
                .map(microBatch -> Integer.parseInt(
                        microBatch.getProperties().getOrDefault("_currentMicroBatch", "1")) == 1)
                .orElse(true);
    }

    static boolean isLastMicroBatch(PipelineMetadata pipelineMetadata) {
        return pipelineMetadata.getBehaviors().stream()
                .filter(behavior -> behavior.getBehaviorType() == BehaviorType.MicroBatch)
                .findFirst()
                .map(microBatch -> Integer.parseInt(
                        microBatch.getProperties().getOrDefault("_currentMicroBatch", "1")) ==
                        Integer.parseInt(microBatch.getProperties().getOrDefault("_lastMicroBatch", "1")))
                .orElse(true);
    }

    static JsonObject sanitize(PipelineMetadata pipelineMetadata) {
        JsonObject serializedPipelineMetadata = PipelineMetadataUtils.serializePipelineMetadata(pipelineMetadata);

        removeTemporaryProperties(serializedPipelineMetadata.getAsJsonObject("sourceResourceToProcess")
                .getAsJsonObject("resourceProperties"));
        removeTemporaryProperties(serializedPipelineMetadata.getAsJsonObject("targetResourceToProcess")
                .getAsJsonObject("resourceProperties"));
        removeTemporaryProperties(serializedPipelineMetadata.getAsJsonObject("globalProperties"));
        serializedPipelineMetadata.getAsJsonArray("behaviors").forEach(behavior ->
                removeTemporaryProperties(behavior.getAsJsonObject().getAsJsonObject("properties")));

        return SecretRedactor.redact(serializedPipelineMetadata);
    }

    static boolean shouldGetNewRecords(Map<String, String> properties) {
        boolean shouldGetNewRecords = Boolean.parseBoolean(properties.getOrDefault("newRecordsOnly", "false"));

        if (shouldGetNewRecords) {
            validateProperties(properties, "newRecordsField", "newRecordsFieldType");
        }

        return shouldGetNewRecords;
    }

    static boolean hasBehavior(PipelineMetadata pmd, BehaviorType behaviorType) {
        return pmd.getBehaviors().stream().anyMatch(behavior -> behavior.getBehaviorType() == behaviorType);
    }

    static boolean useDataStore(PipelineMetadata pipelineMetadata) {
        return Boolean.parseBoolean(pipelineMetadata.getGlobalProperties().getOrDefault("useDataStore", "true"));
    }

    private static void removeTemporaryProperties(JsonObject jsonObject) {
        Set<String> keysToRemove = new HashSet<>();

        jsonObject.keySet().forEach(key -> {
            if (key.startsWith("_")) {
                keysToRemove.add(key);
            }
        });

        keysToRemove.forEach(jsonObject::remove);
    }
}
