package com.everyarrow.dittoe.pipelines.execution;

import com.everyarrow.dittoe.pipelines.behaviors.instrumentation.InMemoryInstrumentationService;
import com.everyarrow.dittoe.pipelines.behaviors.instrumentation.Instrumentation;
import com.everyarrow.dittoe.pipelines.behaviors.instrumentation.RunnerEventInstrumentationService;
import com.everyarrow.dittoe.utility.Config;
import com.everyarrow.dittoe.utility.DataInsertionObserver;
import com.everyarrow.dittoe.utility.DataReadObserver;
import org.everyarrow.dittoe.schema.Behavior;
import org.everyarrow.dittoe.schema.BehaviorType;
import org.everyarrow.dittoe.schema.PipelineMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PipelineInstrumentationTest {
    private final String originalServiceType = Config.INSTRUMENTATION_SERVICE_TYPE;
    private final String originalPipelineStartTime = Config.PIPELINE_START_TIME;

    @AfterEach
    void tearDown() {
        Config.INSTRUMENTATION_SERVICE_TYPE = originalServiceType;
        Config.PIPELINE_START_TIME = originalPipelineStartTime;
        Instrumentation.setInstance(null);
        Instrumentation.setApiKey(null);
    }

    @Test
    void installsDefaultInMemoryInstrumentationWhenPmdOmitsInstrumentation() throws Exception {
        Config.INSTRUMENTATION_SERVICE_TYPE = "runner";

        createPlan(pipelineMetadata(
                "orders-to-ledger",
                behavior("Source Retrieval", BehaviorType.SourceRetrieval),
                behavior("Target Insert Update", BehaviorType.TargetInsertUpdate)));

        Instrumentation instrumentation = Instrumentation.getInstance();
        assertNotNull(instrumentation);
        assertInstanceOf(InMemoryInstrumentationService.class, instrumentation.getService());
        assertEquals("orders-to-ledger", instrumentation.getJob().getName());
    }

    @Test
    void explicitInstrumentationBehaviorIsHonored() throws Exception {
        Config.INSTRUMENTATION_SERVICE_TYPE = "runner";

        createPlan(pipelineMetadata(
                "orders-to-ledger",
                behavior("Instrumentation", BehaviorType.Instrumentation,
                        Map.of("jobName", "explicit-job", "jobId", "job-123")),
                behavior("Source Retrieval", BehaviorType.SourceRetrieval)));

        Instrumentation instrumentation = Instrumentation.getInstance();
        assertNotNull(instrumentation);
        assertInstanceOf(RunnerEventInstrumentationService.class, instrumentation.getService());
        assertEquals("explicit-job", instrumentation.getJob().getName());
    }

    @Test
    void microBatchPlanGetsDefaultInstrumentationBeforeExecutionUsesIt() throws Exception {
        Config.INSTRUMENTATION_SERVICE_TYPE = "runner";

        BehaviorPlan plan = createPlan(pipelineMetadata(
                "microbatch-pipeline",
                behavior("Micro Batch", BehaviorType.MicroBatch,
                        Map.of("chunkSize", "100", "rowNumberColumn", "row_number")),
                behavior("Source Retrieval", BehaviorType.SourceRetrieval)));

        assertNotNull(plan.microBatch());
        Instrumentation instrumentation = Instrumentation.getInstance();
        assertNotNull(instrumentation);
        assertInstanceOf(InMemoryInstrumentationService.class, instrumentation.getService());
        assertEquals("microbatch-pipeline", instrumentation.getJob().getName());
    }

    private static BehaviorPlan createPlan(PipelineMetadata pipelineMetadata) throws Exception {
        return new BehaviorPlanFactory(LoggerFactory.getLogger(PipelineInstrumentationTest.class)).create(
                pipelineMetadata,
                null,
                new DataReadObserver(),
                new DataInsertionObserver(),
                null);
    }

    private static PipelineMetadata pipelineMetadata(String shortName, Behavior... behaviors) {
        PipelineMetadata pipelineMetadata = new PipelineMetadata();
        pipelineMetadata.setShortName(shortName);
        pipelineMetadata.setBehaviors(new ArrayList<>(List.of(behaviors)));
        return pipelineMetadata;
    }

    private static Behavior behavior(String name, BehaviorType behaviorType) {
        return behavior(name, behaviorType, new HashMap<>());
    }

    private static Behavior behavior(String name, BehaviorType behaviorType, Map<String, String> properties) {
        return new Behavior(name, behaviorType, new HashMap<>(properties), name);
    }
}
