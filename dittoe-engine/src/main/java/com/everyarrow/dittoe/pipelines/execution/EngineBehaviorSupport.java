package com.everyarrow.dittoe.pipelines.execution;

import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.Behavior;
import org.everyarrow.dittoe.schema.BehaviorType;
import org.everyarrow.dittoe.schema.PipelineMetadata;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class EngineBehaviorSupport {
    private static final Set<BehaviorType> RUNTIME_SUPPORTED_BEHAVIORS = Collections.unmodifiableSet(EnumSet.of(
            BehaviorType.Formatting,
            BehaviorType.SourceRetrieval,
            BehaviorType.TargetInsertUpdate,
            BehaviorType.Transformation,
            BehaviorType.Encryption,
            BehaviorType.Instrumentation,
            BehaviorType.SchemaValidation,
            BehaviorType.MicroBatch,
            BehaviorType.Compression,
            BehaviorType.Cache));

    private static final Set<BehaviorType> RUNTIME_UNSUPPORTED_BEHAVIORS =
            Collections.unmodifiableSet(EnumSet.complementOf(EnumSet.copyOf(RUNTIME_SUPPORTED_BEHAVIORS)));

    private EngineBehaviorSupport() {
    }

    public static Set<BehaviorType> supportedRuntimeBehaviors() {
        return RUNTIME_SUPPORTED_BEHAVIORS;
    }

    public static Set<BehaviorType> unsupportedRuntimeBehaviors() {
        return RUNTIME_UNSUPPORTED_BEHAVIORS;
    }

    public static boolean supportsRuntimeExecution(BehaviorType behaviorType) {
        return RUNTIME_SUPPORTED_BEHAVIORS.contains(behaviorType);
    }

    public static void validateRuntimeBehaviors(PipelineMetadata pipelineMetadata) throws ExecutionException {
        if (pipelineMetadata == null) {
            throw new ExecutionException("Pipeline metadata cannot be null.");
        }

        List<Behavior> behaviors = pipelineMetadata.getBehaviors();
        if (behaviors == null || behaviors.isEmpty()) {
            return;
        }

        List<String> unsupported = behaviors.stream()
                .filter(behavior -> behavior == null || !supportsRuntimeExecution(behavior.getBehaviorType()))
                .map(EngineBehaviorSupport::describeUnsupportedBehavior)
                .toList();

        if (!unsupported.isEmpty()) {
            throw new ExecutionException(unsupportedBehaviorsMessage(unsupported));
        }
    }

    static String unsupportedBehaviorMessage(BehaviorType behaviorType) {
        return unsupportedBehaviorsMessage(List.of(String.valueOf(behaviorType)));
    }

    private static String describeUnsupportedBehavior(Behavior behavior) {
        if (behavior == null) {
            return "null behavior";
        }

        String behaviorType = String.valueOf(behavior.getBehaviorType());
        String behaviorName = behavior.getName();
        if (behaviorName == null || behaviorName.isBlank()) {
            return behaviorType;
        }
        return behaviorType + " (name: " + behaviorName + ")";
    }

    private static String unsupportedBehaviorsMessage(List<String> unsupportedBehaviors) {
        return "DITTOE Engine cannot execute unsupported behavior type(s): "
                + String.join(", ", unsupportedBehaviors)
                + ". Supported runtime behavior types: "
                + formatBehaviors(RUNTIME_SUPPORTED_BEHAVIORS)
                + ". Schema-only or external-orchestration behavior types must be handled before the PMD "
                + "is submitted to the engine, or removed from the PMD.";
    }

    private static String formatBehaviors(Set<BehaviorType> behaviorTypes) {
        return behaviorTypes.stream()
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }
}
