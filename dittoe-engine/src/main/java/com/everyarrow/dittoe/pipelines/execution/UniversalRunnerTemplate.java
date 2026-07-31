package com.everyarrow.dittoe.pipelines.execution;

import com.everyarrow.dittoe.io.IOServer;
import com.everyarrow.dittoe.io.Reader;
import com.everyarrow.dittoe.io.data.DataStoreException;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.io.data.JavaDataStore;
import com.everyarrow.dittoe.pipelines.behaviors.StagedBehaviorBase;
import com.everyarrow.dittoe.pipelines.behaviors.instrumentation.InMemoryInstrumentationService;
import com.everyarrow.dittoe.pipelines.behaviors.instrumentation.Instrumentation;
import com.everyarrow.dittoe.pipelines.behaviors.instrumentation.InstrumentationService;
import com.everyarrow.dittoe.pipelines.behaviors.microbatch.MicroBatch;
import com.everyarrow.dittoe.pipelines.execution.abstraction.BehaviorException;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.PipelineMetadata;
import com.everyarrow.dittoe.utility.DataInsertionObserver;
import com.everyarrow.dittoe.utility.DataReadObserver;
import com.everyarrow.dittoe.utility.metadata.MetadataRepositoryAPI;
import com.everyarrow.dittoe.utility.metadata.MetadataRepositoryException;
import com.everyarrow.dittoe.utility.metadata.PipelineMetadataHelper;
import org.everyarrow.dittoe.schema.Behavior;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pipeline runner that executes the BAPPTE (Begin, Acquire, Process, Package, Transmit,
 * and End) process. There are 16 default templates for a Pipeline. However, a custom template can be created that
 * can handle resources of different types. This is an implementation of a custom template that can run
 * pipelines of all 16 template types.
 */
public final class UniversalRunnerTemplate extends PipelineRunner {
    private final Logger logger = LoggerFactory.getLogger(UniversalRunnerTemplate.class);
    private MicroBatch microBatch;
    private DataInsertionObserver dataInsertionObserver;
    private DataReadObserver dataReadObserver;
    private PipelineExecutionStats stats;

    /**
     * Constructor.
     */
    public UniversalRunnerTemplate(PipelineMetadata pipelineMetadata) {
        this(pipelineMetadata, null);
    }

    /**
     * Constructor with stats tracking.
     */
    public UniversalRunnerTemplate(PipelineMetadata pipelineMetadata, PipelineExecutionStats stats) {
        this.pipelineMetadata = pipelineMetadata;
        this.stats = stats;
        MetadataRepositoryAPI.setPipelineMetadata(this.pipelineMetadata);
    }


    /**
     * Retrieves Process Metadata Document from the repository.
     * Builds DataStore.
     */
    private void setup() {
        MetadataRepositoryAPI.setPipelineMetadata(this.pipelineMetadata);

        this.stagedBehaviors = new ArrayList<>();
        this.dataInsertionObserver = new DataInsertionObserver();
        this.dataReadObserver = new DataReadObserver();

        // Connect observers to stats
        if (stats != null) {
            this.dataInsertionObserver.setStats(stats);
            this.dataReadObserver.setStats(stats);
        }
    }

    /**
     * Builds the DataStore in which the data will be stored.
     * Persistent data store of size >= 4 GB cannot be created.
     *
     * @return true if data store was built else false
     * @throws ExecutionException Thrown if any error occurs during execution of the process.
     * @throws DataStoreException Thrown if any error occurs while initializing the DataStore object.
     */
    private boolean buildDataStore() throws ExecutionException, DataStoreException, ClassNotFoundException {
        this.getLogger().info("Building DataStore");
        IOServer server = (IOServer) pipelineMetadata.getSourceResourceToProcess().getServer();

        Reader reader = server.getReader();
        long dataCount = reader.getDataCount();

        // Track rows read
        if (stats != null) {
            stats.setRowsRead(dataCount);
        }

        if (dataCount == 0) {
            this.getLogger().info("Not building data store because data count in source resource is 0");
            return false;
        }

        this.dataStore = new JavaDataStore(this.getPipelineMetadata().getSourceResourceToProcess().getResourceName());

        DataStoreInterface.setDataStoreInterface(this.dataStore);
        return true;
    }


    /**
     * Adds the staged behaviors to be executed.
     * Setups non-staged behaviors.
     *
     * @throws BehaviorException Thrown if an unsupported behavior is specified in Process Metadata Document.
     */
    private void setupBehaviors() throws BehaviorException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        BehaviorPlan plan = new BehaviorPlanFactory(getLogger()).create(
                getPipelineMetadata(),
                getDataStore(),
                getDataReadObserver(),
                getDataInsertionObserver(),
                stats);
        this.setStagedBehaviors(plan.stagedBehaviors());
        this.setMicroBatch(plan.microBatch());
    }

    private void executeBehaviors() throws ExecutionException, DataStoreException, ClassNotFoundException, MetadataRepositoryException {
        // Sorting the staged behaviors based on their StageType and Order
        List<StagedBehaviorBase> sortedStagedBehaviors = new ArrayList<>(this.getStagedBehaviors().stream()
                .map(stagedBehaviorBase -> stagedBehaviorBase)
                .sorted(Comparator.comparing(StagedBehaviorBase::getStageType)
                        .thenComparing(StagedBehaviorBase::getOrder)).toList());

        String stagedBehaviorsInOrder =
                sortedStagedBehaviors.stream()
                        .map(Behavior::getName)
                        .collect(Collectors.joining(", "));

        this.getLogger().info("Following is the order of execution of the staged behaviors: {}",
                stagedBehaviorsInOrder);

        PipelineStageExecutor stageExecutor = createStageExecutor();
        if (this.getMicroBatch() != null) {
            MicroBatchExecutionPlan microBatchExecutionPlan = MicroBatchExecutionPlan.from(this.getMicroBatch());
            this.getLogger().info("Total micro batches to process: {}", microBatchExecutionPlan.totalBatchCount());
            Instrumentation.getInstance().addStatisticsToJob(microBatchExecutionPlan.statistics());
            microBatchExecutionPlan.applyRangeProperties();

            int batchIndex = microBatchExecutionPlan.fromIndex();
            for (; batchIndex <= microBatchExecutionPlan.toIndex(); batchIndex++) {
                microBatchExecutionPlan.applyCurrentBatch(batchIndex);
                // save the current micro batch so that if the pipeline fails it will continue from this batch
                PipelineMetadataHelper.saveCurrentMicroBatch(this.getPipelineMetadata());

                this.getLogger().info("Processing batch {} of total {} batches",
                        batchIndex + 1, microBatchExecutionPlan.lastBatchNumber());

                stageExecutor.executeStagedBehaviors(sortedStagedBehaviors);
            }

            // reset the batch number if the current batch is the last batch according to the total batches
            if (microBatchExecutionPlan.completedAllBatches(batchIndex)) {
                this.getMicroBatch().getProperties().put(MicroBatch.FROM_BATCH, "1");
                PipelineMetadataHelper.saveCurrentMicroBatch(this.getPipelineMetadata());
            }
        } else {
            stageExecutor.executeStagedBehaviors(sortedStagedBehaviors);
        }
    }

    private PipelineStageExecutor createStageExecutor() {
        return new PipelineStageExecutor(
                getPipelineMetadata(),
                stats,
                getLogger(),
                new RunnerDataStoreController(),
                this::getMicroBatch);
    }

    /**
     * Runs the BAPPTE process.
     */
    @Override
    public void run() throws ExecutionException {
        Exception exception = null;
        long totalStartTime = System.currentTimeMillis();
        try {
            this.getLogger().info("Setting up");
            this.setup();

            this.getLogger().info("Setting up behaviors");
            this.setupBehaviors();

            IOServer server = (IOServer) pipelineMetadata.getSourceResourceToProcess().getServer();
            long dataCount = server.getReader().getDataCount();

            this.getLogger().info("Total records in the source resource: {}", dataCount);

            if (dataCount == 0) {
                this.getLogger().info("Exiting because data count in source resource is 0");
                return;
            }

            // Executing the behaviors
            this.executeBehaviors();

            if (this.getDataStore() != null) {
                this.getDataStore().close();
            }

            PipelineMetadataHelper.saveNewRecordsFieldValue(this.getPipelineMetadata());
        } catch (Exception e) {
            exception = e;
        } finally {

            long totalEndTime = System.currentTimeMillis();
            long totalDuration = totalEndTime - totalStartTime;

            this.getLogger().info("Job ended with total time: {} (milliseconds), {} (seconds)",
                    totalDuration, totalDuration / 1000.0);

            // Output in-memory instrumentation logs if using in-memory service
            if (Instrumentation.getInstance() != null) {
                InstrumentationService service = Instrumentation.getInstance().getService();
                if (service instanceof InMemoryInstrumentationService) {
                    ((InMemoryInstrumentationService) service).outputLogs();
                }
            }
        }

        if (exception != null)
            throw new ExecutionException(exception);
    }

    public MicroBatch getMicroBatch() {
        return microBatch;
    }

    public void setMicroBatch(MicroBatch microBatch) {
        this.microBatch = microBatch;
    }

    public Logger getLogger() {
        return logger;
    }

    public DataInsertionObserver getDataInsertionObserver() {
        return dataInsertionObserver;
    }

    public DataReadObserver getDataReadObserver() {
        return dataReadObserver;
    }

    public PipelineExecutionStats getStats() {
        return stats;
    }

    public void setStats(PipelineExecutionStats stats) {
        this.stats = stats;
    }

    private final class RunnerDataStoreController implements PipelineStageExecutor.DataStoreController {
        @Override
        public DataStoreInterface getDataStore() {
            return UniversalRunnerTemplate.this.getDataStore();
        }

        @Override
        public void setDataStore(DataStoreInterface dataStore) {
            UniversalRunnerTemplate.this.setDataStore(dataStore);
        }

        @Override
        public boolean buildDataStore() throws ExecutionException, DataStoreException, ClassNotFoundException {
            return UniversalRunnerTemplate.this.buildDataStore();
        }
    }
}
