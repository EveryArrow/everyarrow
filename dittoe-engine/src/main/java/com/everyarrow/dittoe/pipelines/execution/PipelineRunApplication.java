package com.everyarrow.dittoe.pipelines.execution;

import com.everyarrow.dittoe.pipelines.behaviors.instrumentation.Instrumentation;
import com.everyarrow.dittoe.utility.Config;
import com.everyarrow.dittoe.utility.Utils;
import com.everyarrow.dittoe.utility.metadata.MetadataRepositoryAPI;
import com.everyarrow.dittoe.utility.metadata.MetadataRepositoryException;
import com.everyarrow.dittoe.utility.metadata.PipelineMetadataHelper;
import com.google.common.reflect.TypeToken;
import org.everyarrow.dittoe.schema.BehaviorType;
import org.everyarrow.dittoe.schema.PipelineMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

final class PipelineRunApplication {
    private Logger logger;

    void run(String[] args) throws Exception {
        PipelineRunRequest request = PipelineRunCommand.parse(args);
        PipelineMetadataLoader metadataLoader = new PipelineMetadataLoader();
        PipelineRuntimeContext runtimeContext = metadataLoader.loadRuntimeContext(request);

        runtimeContext.initializeRuntime();
        logger = LoggerFactory.getLogger(PipelineRunApplication.class);
        logger.info("Logger initialized with log directory: {}", runtimeContext.logDirectory());
        logger.info("Created pipeline run directory: {}", runtimeContext.logDirectory());
        logger.info(runtimeContext.instrumentationMode());

        logger.info("Loading Pipeline Schema from file: {}", runtimeContext.schemaFile());
        PipelineMetadata pipelineMetadata = metadataLoader.loadMetadata(runtimeContext);
        logger.info("Processing {}", request.pipelineName());

        EngineBehaviorSupport.validateRuntimeBehaviors(pipelineMetadata);
        syncFields(pipelineMetadata);
        if (!runTest(pipelineMetadata)) {
            return;
        }

        executePipeline(runtimeContext, pipelineMetadata);
    }

    private void syncFields(PipelineMetadata pipelineMetadata) throws Exception {
        if (Boolean.parseBoolean(pipelineMetadata.getGlobalProperties().getOrDefault("syncFields", "false"))) {
            logger.info("Syncing Fields");
            PipelineMetadataHelper.syncFields(pipelineMetadata);
            logger.info("Fields synced successfully");
        }
    }

    private boolean runTest(PipelineMetadata pipelineMetadata) throws Exception {
        if (!Boolean.parseBoolean(pipelineMetadata.getGlobalProperties().getOrDefault("runTest", "false"))) {
            logger.info("Skipping test");
            return true;
        }

        logger.info("Preparing to run test");
        logger.info("Test configuration applied");
        logger.info("Running test");
        AtomicReference<UniversalRunnerTemplate> runnerReference = new AtomicReference<>();
        PipelineTestMode.runWithTestConfiguration(pipelineMetadata, () -> {
            UniversalRunnerTemplate runner = new UniversalRunnerTemplate(pipelineMetadata);
            runnerReference.set(runner);
            runner.run();
        });
        logger.info("Test configuration restored");

        UniversalRunnerTemplate runner = runnerReference.get();
        if (runner.getDataInsertionObserver().isRecordInserted()) {
            logger.info("Test passed");
            return true;
        }

        logger.error("Test failed");
        return false;
    }

    private void executePipeline(PipelineRuntimeContext runtimeContext, PipelineMetadata pipelineMetadata)
            throws Exception {
        logger.info("Running complete pipeline");
        PipelineExecutionStats stats = new PipelineExecutionStats();
        stats.start();
        stats.setJobBatched(pipelineMetadata.getBehaviors().stream()
                .anyMatch(behavior -> behavior.getBehaviorType() == BehaviorType.MicroBatch));

        try {
            savePipelineMetadata(runtimeContext, pipelineMetadata);
            UniversalRunnerTemplate runner = new UniversalRunnerTemplate(pipelineMetadata, stats);
            runner.run();
        } finally {
            stats.finish();
            savePipelineStats(runtimeContext, stats);
            savePipelineMetadata(runtimeContext, pipelineMetadata);
        }
    }

    private void savePipelineStats(PipelineRuntimeContext context, PipelineExecutionStats stats) throws IOException {
        Path summaryFile = Path.of(Utils.getPipelinePath(
                context.request().pipelineName(),
                context.pipelineStartTime(),
                "summary.json"));
        Files.writeString(summaryFile, stats.toJson(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        logger.info("Pipeline statistics saved to: {}", summaryFile);

        Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> statsMap = Utils.getGson().fromJson(Utils.getGson().toJson(stats), mapType);
        if (Instrumentation.getInstance() != null) {
            Instrumentation.getInstance().addStatisticsToJob(statsMap);
        }
    }

    private void savePipelineMetadata(PipelineRuntimeContext context, PipelineMetadata pipelineMetadata)
            throws MetadataRepositoryException {
        new MetadataRepositoryAPI().upsert(pipelineMetadata);
        logger.info("Pipeline metadata saved to: {}",
                Utils.getPipelinePath(Config.PIPELINE_NAME, context.pipelineStartTime(), "pmd.json"));
    }
}
