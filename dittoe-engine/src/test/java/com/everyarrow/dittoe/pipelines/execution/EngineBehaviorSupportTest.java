package com.everyarrow.dittoe.pipelines.execution;

import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.Behavior;
import org.everyarrow.dittoe.schema.BehaviorType;
import org.everyarrow.dittoe.schema.PipelineMetadata;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineBehaviorSupportTest {
    @Test
    void allSchemaBehaviorTypesAreClassifiedForRuntimeSupport() {
        EnumSet<BehaviorType> classified = EnumSet.copyOf(EngineBehaviorSupport.supportedRuntimeBehaviors());
        classified.addAll(EngineBehaviorSupport.unsupportedRuntimeBehaviors());

        assertEquals(EnumSet.allOf(BehaviorType.class), classified);
        assertTrue(EngineBehaviorSupport.supportedRuntimeBehaviors().stream()
                .noneMatch(EngineBehaviorSupport.unsupportedRuntimeBehaviors()::contains));
    }

    @Test
    void validatesRuntimeSupportedBehaviors() throws ExecutionException {
        PipelineMetadata pipelineMetadata = pipelineMetadata(
                behavior("SourceRetrieval", BehaviorType.SourceRetrieval),
                behavior("Transformation", BehaviorType.Transformation),
                behavior("TargetInsertUpdate", BehaviorType.TargetInsertUpdate));

        EngineBehaviorSupport.validateRuntimeBehaviors(pipelineMetadata);
    }

    @Test
    void rejectsSchemaOnlyBehaviorsBeforeExecution() {
        PipelineMetadata pipelineMetadata = pipelineMetadata(
                behavior("SourceRetrieval", BehaviorType.SourceRetrieval),
                behavior("Audit Log", BehaviorType.Logging));

        ExecutionException exception = assertThrows(ExecutionException.class,
                () -> EngineBehaviorSupport.validateRuntimeBehaviors(pipelineMetadata));

        assertTrue(exception.getMessage().contains("Logging"));
        assertTrue(exception.getMessage().contains("Audit Log"));
        assertTrue(exception.getMessage().contains("Supported runtime behavior types"));
    }

    @Test
    void unsupportedRuntimeBehaviorSetIsNotEmpty() {
        assertFalse(EngineBehaviorSupport.unsupportedRuntimeBehaviors().isEmpty());
        assertTrue(EngineBehaviorSupport.unsupportedRuntimeBehaviors().contains(BehaviorType.CustomCode));
    }

    private static PipelineMetadata pipelineMetadata(Behavior... behaviors) {
        PipelineMetadata pipelineMetadata = new PipelineMetadata();
        pipelineMetadata.setBehaviors(List.of(behaviors));
        return pipelineMetadata;
    }

    private static Behavior behavior(String name, BehaviorType behaviorType) {
        return new Behavior(name, behaviorType, new HashMap<>(), name);
    }
}
