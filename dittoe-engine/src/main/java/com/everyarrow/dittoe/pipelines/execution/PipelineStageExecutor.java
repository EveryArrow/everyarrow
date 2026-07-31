package com.everyarrow.dittoe.pipelines.execution;

import com.everyarrow.dittoe.io.data.DataStoreException;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.pipelines.behaviors.StagedBehaviorBase;
import com.everyarrow.dittoe.pipelines.behaviors.instrumentation.Instrumentation;
import com.everyarrow.dittoe.pipelines.behaviors.instrumentation.InstrumentationTask;
import com.everyarrow.dittoe.pipelines.behaviors.instrumentation.TaskExecutionHandle;
import com.everyarrow.dittoe.pipelines.behaviors.microbatch.MicroBatch;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import com.everyarrow.dittoe.utility.Utils;
import org.everyarrow.dittoe.schema.PipelineMetadata;
import org.everyarrow.dittoe.schema.StageType;
import org.slf4j.Logger;

import java.util.List;
import java.util.function.Supplier;

final class PipelineStageExecutor {
    private final PipelineMetadata pipelineMetadata;
    private final PipelineExecutionStats stats;
    private final Logger logger;
    private final DataStoreController dataStoreController;
    private final Supplier<MicroBatch> microBatchSupplier;

    PipelineStageExecutor(PipelineMetadata pipelineMetadata,
                          PipelineExecutionStats stats,
                          Logger logger,
                          DataStoreController dataStoreController,
                          Supplier<MicroBatch> microBatchSupplier) {
        this.pipelineMetadata = pipelineMetadata;
        this.stats = stats;
        this.logger = logger;
        this.dataStoreController = dataStoreController;
        this.microBatchSupplier = microBatchSupplier;
    }

    void executeStagedBehaviors(List<StagedBehaviorBase> stagedBehaviors)
            throws ExecutionException, DataStoreException, ClassNotFoundException {
        if (dataStoreController.getDataStore() != null) {
            dataStoreController.getDataStore().close();
            dataStoreController.setDataStore(null);
        }

        boolean dataStoreBuilt = false;
        for (StagedBehaviorBase stagedBehavior : stagedBehaviors) {
            if (Utils.useDataStore(pipelineMetadata)
                    && !dataStoreBuilt
                    && stagedBehavior.getStageType() == StageType.Acquire) {
                if (!dataStoreController.buildDataStore()) {
                    return;
                }
                dataStoreBuilt = true;
            }

            if (dataStoreBuilt) {
                stagedBehavior.setDataStore(dataStoreController.getDataStore());
            }

            executeBehavior(stagedBehavior);
        }
    }

    private void executeBehavior(StagedBehaviorBase stagedBehavior) throws ExecutionException {
        logger.info("Executing behavior: {}", stagedBehavior.getName());

        long startTime = System.currentTimeMillis();
        Exception exception = null;
        PipelineExecutionStats.StageStats stageStats = null;
        if (stats != null) {
            stageStats = stats.startStage(stagedBehavior.getName());
            stagedBehavior.setStageStats(stageStats);
        }

        InstrumentationTask task = Instrumentation.getInstance()
                .getOrCreateTask(stagedBehavior.getName(), stagedBehavior.getDescription());
        TaskExecutionHandle startTaskResponse = Instrumentation.getInstance().startTask(task.getId());
        Instrumentation.getInstance().getRunningTasks().put(task, startTaskResponse);

        MicroBatch microBatch = microBatchSupplier.get();
        if (microBatch != null) {
            Instrumentation.getInstance().addMessagesToTask(
                    Instrumentation.getInstance().getRunningTaskExecutionId(stagedBehavior.getName()),
                    List.of("Current micro batch " + microBatch.getProperties().get("_currentMicroBatch")));
        }

        try {
            stagedBehavior.exec();
        } catch (Exception e) {
            exception = e;
            if (stageStats != null && stageStats.getRowsErrored() == 0) {
                stageStats.incrementRowsErrored(1);
            }
            throw new ExecutionException(exception);
        } finally {
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            if (stageStats != null) {
                stageStats.stop();
            }

            if (startTaskResponse != null) {
                Instrumentation.getInstance().endTask(
                        startTaskResponse.getTaskExecutionId(),
                        exception != null ? exception.getMessage() : null);
                Instrumentation.getInstance().getRunningTasks().remove(task);
            }

            logger.info("{} ended with total time: {} (milliseconds), {} (seconds)",
                    stagedBehavior.getName(), duration, duration / 1000.0);
        }
    }

    interface DataStoreController {
        DataStoreInterface getDataStore();

        void setDataStore(DataStoreInterface dataStore);

        boolean buildDataStore() throws ExecutionException, DataStoreException, ClassNotFoundException;
    }
}
