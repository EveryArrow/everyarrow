package com.everyarrow.dittoe.pipelines.execution;

import com.everyarrow.dittoe.pipelines.behaviors.instrumentation.Instrumentation;
import com.everyarrow.dittoe.pipelines.execution.abstraction.BehaviorException;
import org.everyarrow.dittoe.schema.PipelineMetadata;
import org.slf4j.Logger;

import java.util.Map;

final class PipelineInstrumentation {
    private static final String DEFAULT_NAME = "Instrumentation";
    private static final String DEFAULT_DESCRIPTION = "Default in-memory runtime instrumentation";

    private final Logger logger;

    PipelineInstrumentation(Logger logger) {
        this.logger = logger;
    }

    void ensureAvailable(PipelineMetadata pipelineMetadata, boolean explicitInstrumentation)
            throws BehaviorException {
        if (explicitInstrumentation) {
            return;
        }

        logger.info("No Instrumentation behavior provided; using default in-memory instrumentation");
        Instrumentation.setInstance(Instrumentation.inMemory(
                DEFAULT_NAME,
                DEFAULT_DESCRIPTION,
                Map.of("jobName", jobName(pipelineMetadata))));
    }

    private static String jobName(PipelineMetadata pipelineMetadata) {
        if (pipelineMetadata == null || pipelineMetadata.getShortName() == null
                || pipelineMetadata.getShortName().isBlank()) {
            return "pipeline";
        }
        return pipelineMetadata.getShortName();
    }
}
