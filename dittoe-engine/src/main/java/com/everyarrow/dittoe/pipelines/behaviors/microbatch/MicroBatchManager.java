package com.everyarrow.dittoe.pipelines.behaviors.microbatch;

import org.everyarrow.dittoe.schema.Behavior;
import org.everyarrow.dittoe.schema.BehaviorType;
import com.everyarrow.dittoe.utility.metadata.MetadataRepositoryAPI;

import java.util.Map;
import java.util.Optional;

public class MicroBatchManager {
    public static Batch getBatch() {
        Optional<Behavior> microBatch = MetadataRepositoryAPI.getPipelineMetadata().getBehaviors().stream().filter(
                behavior -> behavior.getBehaviorType() == BehaviorType.MicroBatch
        ).findFirst();

        if (microBatch.isEmpty())
            return null;

        Map<String, String> microBatchProperties = microBatch.get().getProperties();

        for (String key : new String[]{"rowNumberColumn", "_rowNumberStart", "_rowNumberEnd"}) {
            if (!microBatchProperties.containsKey(key))
                return null;
        }

        String rowNumberColumn = microBatchProperties.get("rowNumberColumn");
        String rowNumberStart = microBatchProperties.get("_rowNumberStart");
        String rowNumberEnd = microBatchProperties.get("_rowNumberEnd");

        return new Batch(rowNumberColumn, rowNumberStart, rowNumberEnd);
    }

    public record Batch(String fieldName, String valueStart, String valueEnd) {
    }
}
