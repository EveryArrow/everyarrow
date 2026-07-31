package com.everyarrow.dittoe.pipelines.execution;

import org.everyarrow.dittoe.schema.Behavior;
import org.everyarrow.dittoe.schema.BehaviorType;
import org.everyarrow.dittoe.schema.PipelineMetadata;
import org.everyarrow.dittoe.schema.Resource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PipelineTestModeTest {
    @Test
    void restoresMicroBatchAndExistingLimitWhenTestRunFails() {
        Behavior sourceRetrieval = behavior("Source Retrieval", BehaviorType.SourceRetrieval);
        Behavior microBatch = behavior("Micro Batch", BehaviorType.MicroBatch);
        Behavior targetInsertUpdate = behavior("Target Insert Update", BehaviorType.TargetInsertUpdate);
        PipelineMetadata pipelineMetadata = pipelineMetadata("25", sourceRetrieval, microBatch, targetInsertUpdate);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> PipelineTestMode.runWithTestConfiguration(pipelineMetadata, () -> {
                    assertFalse(pipelineMetadata.getBehaviors().contains(microBatch));
                    assertEquals("100", pipelineMetadata.getSourceResourceToProcess()
                            .getResourceProperties()
                            .get("limit"));
                    throw new IllegalStateException("test failure");
                }));

        assertEquals("test failure", thrown.getMessage());
        assertSame(sourceRetrieval, pipelineMetadata.getBehaviors().get(0));
        assertSame(microBatch, pipelineMetadata.getBehaviors().get(1));
        assertSame(targetInsertUpdate, pipelineMetadata.getBehaviors().get(2));
        assertEquals("25", pipelineMetadata.getSourceResourceToProcess()
                .getResourceProperties()
                .get("limit"));
    }

    @Test
    void removesTemporaryLimitWhenNoOriginalLimitExisted() throws Exception {
        Behavior sourceRetrieval = behavior("Source Retrieval", BehaviorType.SourceRetrieval);
        PipelineMetadata pipelineMetadata = pipelineMetadata(null, sourceRetrieval);

        PipelineTestMode.runWithTestConfiguration(pipelineMetadata, () ->
                assertEquals("100", pipelineMetadata.getSourceResourceToProcess()
                        .getResourceProperties()
                        .get("limit")));

        assertSame(sourceRetrieval, pipelineMetadata.getBehaviors().getFirst());
        assertFalse(pipelineMetadata.getSourceResourceToProcess()
                .getResourceProperties()
                .containsKey("limit"));
    }

    private static PipelineMetadata pipelineMetadata(String limit, Behavior... behaviors) {
        Resource sourceResource = new Resource();
        Map<String, String> resourceProperties = new HashMap<>();
        if (limit != null) {
            resourceProperties.put("limit", limit);
        }
        sourceResource.setResourceProperties(resourceProperties);

        PipelineMetadata pipelineMetadata = new PipelineMetadata();
        pipelineMetadata.setSourceResourceToProcess(sourceResource);
        pipelineMetadata.setBehaviors(new ArrayList<>(List.of(behaviors)));
        return pipelineMetadata;
    }

    private static Behavior behavior(String name, BehaviorType behaviorType) {
        return new Behavior(name, behaviorType, new HashMap<>(), name);
    }
}
