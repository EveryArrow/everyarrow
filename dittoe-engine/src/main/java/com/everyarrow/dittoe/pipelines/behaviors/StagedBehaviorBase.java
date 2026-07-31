package com.everyarrow.dittoe.pipelines.behaviors;


import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.pipelines.execution.PipelineExecutionStats;
import com.everyarrow.dittoe.pipelines.execution.abstraction.BehaviorException;
import org.everyarrow.dittoe.schema.PipelineMetadata;
import org.everyarrow.dittoe.schema.StagedBehavior;

/**
 * This class is an extension of StagedBehavior. It contains the Process Metadata Document and the DataStore object.
 */
public abstract class StagedBehaviorBase extends StagedBehavior {
    /**
     * Process Metadata Document that specifies the schema for a pipeline.
     */
    private PipelineMetadata pipelineMetadata;
    /**
     * The data retrieved from source resource is stored in this object. Different staged behaviors may perform
     * manipulations on the data stored in it. For example: Transformation.
     */
    private DataStoreInterface dataStore;
    /**
     * Stage statistics for tracking rows processed and errored in this specific behavior.
     */
    private PipelineExecutionStats.StageStats stageStats;

    /**
     * Constructor used to specify the plugin reference
     *
     * @param pluginReference Plugin reference
     */
    private StagedBehaviorBase(String pluginReference) {
        super(pluginReference);
    }

    /**
     * Parameterized constructor
     *
     * @param stagedBehaviorInput Object containing data to initialize a staged behavior
     */
    public StagedBehaviorBase(StagedBehaviorInput stagedBehaviorInput) {
        super(stagedBehaviorInput.getName(), stagedBehaviorInput.getBehaviorType(), stagedBehaviorInput.getProperties(),
                stagedBehaviorInput.getDescription(), stagedBehaviorInput.getOrder(), stagedBehaviorInput.getStageType());
        this.pipelineMetadata = stagedBehaviorInput.getPipelineMetadata();
        this.dataStore = stagedBehaviorInput.getDataStore();
    }

    /**
     * Gets the Process Metadata Document that the staged behavior is associated with
     *
     * @return Process Metadata Document that the staged behavior is associated with
     */
    public PipelineMetadata getPipelineMetadata() {
        return pipelineMetadata;
    }

    /**
     * Sets the Process Metadata Document that the staged behavior is associated with
     *
     * @param pipelineMetadata Process Metadata Document that is to be set
     */
    public void setPipelineMetadata(PipelineMetadata pipelineMetadata) {
        this.pipelineMetadata = pipelineMetadata;
    }

    /**
     * Gets the DataStore on which the staged behavior operates
     *
     * @return DataStore on which the staged behavior operates
     */
    public DataStoreInterface getDataStore() {
        return dataStore;
    }

    /**
     * Sets the DataStore on which the staged behavior operates
     *
     * @param dataStore DataStore that is to be set
     */
    public void setDataStore(DataStoreInterface dataStore) {
        this.dataStore = dataStore;
    }

    /**
     * Gets the stage statistics for this behavior
     *
     * @return Stage statistics
     */
    public PipelineExecutionStats.StageStats getStageStats() {
        return stageStats;
    }

    /**
     * Sets the stage statistics for this behavior
     *
     * @param stageStats Stage statistics to set
     */
    public void setStageStats(PipelineExecutionStats.StageStats stageStats) {
        this.stageStats = stageStats;
    }

    /**
     * This is the main execution method of a staged behavior. All staged behaviors override this method
     * to specify their execution logic.
     *
     * @throws BehaviorException Thrown if any error occurs during execution of the staged behavior.
     */
    public abstract void exec() throws BehaviorException;
}
