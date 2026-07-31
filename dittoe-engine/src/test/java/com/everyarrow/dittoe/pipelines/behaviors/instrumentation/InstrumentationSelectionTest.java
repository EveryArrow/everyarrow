package com.everyarrow.dittoe.pipelines.behaviors.instrumentation;

import com.everyarrow.dittoe.utility.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class InstrumentationSelectionTest {

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
    void defaultsToInMemoryInstrumentation() {
        assertEquals("inmemory", originalServiceType);
    }

    @Test
    void createsInMemoryServiceWhenInMemoryBackendIsRequested() throws Exception {
        Config.INSTRUMENTATION_SERVICE_TYPE = "inmemory";
        Config.PIPELINE_START_TIME = "2026-04-22-12-00-00";

        Instrumentation instrumentation = new Instrumentation(
                "Instrumentation",
                "Test instrumentation",
                Map.of("jobName", "unit-test-job"));

        assertInstanceOf(InMemoryInstrumentationService.class, instrumentation.getService());
        assertEquals("unit-test-job", instrumentation.getJob().getName());
    }

    @Test
    void createsRunnerEventServiceWhenRunnerBackendIsRequested() throws Exception {
        Config.INSTRUMENTATION_SERVICE_TYPE = "runner";
        Config.PIPELINE_START_TIME = "2026-04-22-12-00-00";

        Instrumentation instrumentation = new Instrumentation(
                "Instrumentation",
                "Test instrumentation",
                Map.of("jobName", "unit-test-job"));

        assertInstanceOf(RunnerEventInstrumentationService.class, instrumentation.getService());
        assertEquals("unit-test-job", instrumentation.getJob().getName());
    }
}
