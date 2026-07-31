package com.everyarrow.dittoe.pipelines.execution;

import com.everyarrow.dittoe.Log4jConfig;
import com.everyarrow.dittoe.utility.Config;
import com.everyarrow.dittoe.utility.security.SafePathResolver;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record PipelineRuntimeContext(
        PipelineRunRequest request,
        Path schemaFile,
        JsonObject schemaJson,
        String pipelineStartTime) {

    Path logDirectory() {
        return SafePathResolver.resolveUnderBase(
                Path.of(Config.LOG_ROOT, "dittoe"),
                request.pipelineName(),
                pipelineStartTime);
    }

    void initializeRuntime() throws IOException {
        Config.PIPELINE_NAME = request.pipelineName();
        Config.PIPELINE_START_TIME = pipelineStartTime;
        Files.createDirectories(logDirectory());
        Log4jConfig.configure(logDirectory().toString());
        if (!"runner".equalsIgnoreCase(Config.INSTRUMENTATION_SERVICE_TYPE)) {
            Config.INSTRUMENTATION_SERVICE_TYPE = "inmemory";
        }
    }

    String instrumentationMode() {
        return "runner".equalsIgnoreCase(Config.INSTRUMENTATION_SERVICE_TYPE)
                ? "Runner event instrumentation enabled"
                : "In-memory instrumentation enabled";
    }
}
