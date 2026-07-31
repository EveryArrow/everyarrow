package com.everyarrow.dittoe.pipelines.execution;

import com.everyarrow.dittoe.utility.Config;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PipelineRuntimeContextTest {

    @Test
    void placesPipelineLogsUnderTheDittoeComponentDirectory() {
        String originalLogRoot = Config.LOG_ROOT;
        Path logRoot = Path.of("target", "runtime-context-logs");
        Config.LOG_ROOT = logRoot.toString();

        try {
            PipelineRuntimeContext context = new PipelineRuntimeContext(
                    new PipelineRunRequest("orders"),
                    Path.of("pmd.json"),
                    new JsonObject(),
                    "2026-07-21T120000");

            assertEquals(
                    logRoot.toAbsolutePath().normalize()
                            .resolve("dittoe")
                            .resolve("orders")
                            .resolve("2026-07-21T120000"),
                    context.logDirectory());
        } finally {
            Config.LOG_ROOT = originalLogRoot;
        }
    }

    @Test
    void rejectsPipelineNamesThatEscapeTheDittoeLogDirectory() {
        String originalLogRoot = Config.LOG_ROOT;
        Config.LOG_ROOT = Path.of("target", "runtime-context-logs").toString();

        try {
            PipelineRuntimeContext context = new PipelineRuntimeContext(
                    new PipelineRunRequest(".."),
                    Path.of("pmd.json"),
                    new JsonObject(),
                    "2026-07-21T120000");

            assertThrows(IllegalArgumentException.class, context::logDirectory);
        } finally {
            Config.LOG_ROOT = originalLogRoot;
        }
    }
}
