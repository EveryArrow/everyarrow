package com.everyarrow.dittoe.pipelines.execution;

import org.everyarrow.dittoe.schema.Behavior;
import org.everyarrow.dittoe.schema.BehaviorType;
import org.everyarrow.dittoe.schema.PipelineMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class PipelineTestMode {
    private static final String LIMIT_PROPERTY = "limit";
    private static final String TEST_LIMIT = "100";

    private PipelineTestMode() {
    }

    static void runWithTestConfiguration(PipelineMetadata pipelineMetadata, ThrowingPipelineAction action) throws Exception {
        List<Behavior> originalBehaviors = new ArrayList<>(pipelineMetadata.getBehaviors());
        Map<String, String> resourceProperties = pipelineMetadata.getSourceResourceToProcess().getResourceProperties();
        boolean hadLimit = resourceProperties.containsKey(LIMIT_PROPERTY);
        String originalLimit = resourceProperties.get(LIMIT_PROPERTY);

        pipelineMetadata.setBehaviors(new ArrayList<>(originalBehaviors.stream()
                .filter(behavior -> behavior.getBehaviorType() != BehaviorType.MicroBatch)
                .toList()));
        resourceProperties.put(LIMIT_PROPERTY, TEST_LIMIT);

        try {
            action.run();
        } finally {
            pipelineMetadata.setBehaviors(new ArrayList<>(originalBehaviors));
            if (hadLimit) {
                resourceProperties.put(LIMIT_PROPERTY, originalLimit);
            } else {
                resourceProperties.remove(LIMIT_PROPERTY);
            }
        }
    }

    @FunctionalInterface
    interface ThrowingPipelineAction {
        void run() throws Exception;
    }
}
