package com.everyarrow.dittoe.pipelines.execution;

import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.pipelines.behaviors.StagedBehaviorBase;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.PipelineMetadata;

import java.util.List;

/**
 * Top level class that runs a Pipeline.
 */
public abstract class PipelineRunner {

    /**
     * Process Metadata Document of the Pipeline.
     */
    protected PipelineMetadata pipelineMetadata;

    /**
     * List of Staged Behaviors that are executed in the Pipeline.
     */
    protected List<StagedBehaviorBase> stagedBehaviors;

    /**
     * DataStore that stores the data of the Pipeline.
     */
    protected DataStoreInterface dataStore;

    /**
     * Gets the Process Metadata Document of the Pipeline.
     *
     * @return Process Metadata Document of the Pipeline.
     */
    public PipelineMetadata getPipelineMetadata() {
        return pipelineMetadata;
    }

    /**
     * Sets the Process Metadata Document.
     *
     * @param pipelineMetadata Process Metadata Document that is to be set.
     */
    public void setPipelineMetadata(PipelineMetadata pipelineMetadata) {
        this.pipelineMetadata = pipelineMetadata;
    }

    /**
     * Gets the list of staged behaviors that are executed during the execution of the Pipeline.
     *
     * @return List of staged behaviors.
     */
    public List<StagedBehaviorBase> getStagedBehaviors() {
        return stagedBehaviors;
    }

    /**
     * Sets the list of staged behaviors that are executed in the Pipeline.
     *
     * @param stagedBehaviors List of staged behaviors that is to be set.
     */
    public void setStagedBehaviors(List<StagedBehaviorBase> stagedBehaviors) {
        this.stagedBehaviors = stagedBehaviors;
    }

    /**
     * Gets the DataStore containing the data.
     *
     * @return DataStore containing the data.
     */
    public DataStoreInterface getDataStore() {
        return dataStore;
    }

    /**
     * Sets the DataStore that contains the data.
     *
     * @param dataStore DataStore that is to be set.
     */
    public void setDataStore(DataStoreInterface dataStore) {
        this.dataStore = dataStore;
    }

    /**
     * This method is used to run the Pipeline.
     *
     * @throws ExecutionException Thrown if any error occurs during the execution of the Pipeline.
     */
    public abstract void run() throws ExecutionException;
}
