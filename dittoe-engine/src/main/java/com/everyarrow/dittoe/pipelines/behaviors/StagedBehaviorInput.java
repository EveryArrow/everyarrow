package com.everyarrow.dittoe.pipelines.behaviors;

import com.everyarrow.dittoe.io.data.DataStoreInterface;
import org.everyarrow.dittoe.schema.BehaviorType;
import org.everyarrow.dittoe.schema.PipelineMetadata;
import org.everyarrow.dittoe.schema.StageType;

import java.util.HashMap;
import java.util.Map;

/**
 * Class representing the input of a staged behavior.
 */
public class StagedBehaviorInput {

    /**
     * Name of the behavior.
     */
    String name;

    /**
     * Type of the behavior.
     */
    BehaviorType behaviorType;

    /**
     * Order of the behavior. This is the order in which staged behaviors of same stage type will be executed.
     */
    int order;

    /**
     * Stage type of the behavior. Represent one of the stage of BAPPTE (Begin, Acquire, Process, Package,
     * Transmit, and End) process.
     */
    StageType stageType;

    /**
     * Process Metadata Document that the staged behavior is associated with
     */
    PipelineMetadata pipelineMetadata;

    /**
     * DataStore on which the staged behavior will perform CRUD operations
     */
    DataStoreInterface dataStore;

    /**
     * Properties of the behavior.
     */
    Map<String, String> properties;

    /**
     * Description of the behavior.
     */
    String description;

    /**
     * Constructor
     *
     * @param name             Name of the behavior.
     * @param behaviorType     Type of the behavior.
     * @param order            Order of the behavior,
     * @param stageType        Stage type of the behavior.
     * @param properties       Properties of the behavior.
     * @param description      Description of the behavior.
     * @param pipeLineMetaData Process Metadata Document that behavior is associated with.
     * @param dataStore        DataStore representing the data
     */
    public StagedBehaviorInput(String name, BehaviorType behaviorType, Map<String, String> properties,
                               String description, int order, StageType stageType,
                               PipelineMetadata pipeLineMetaData, DataStoreInterface dataStore) {
        this.name = name;
        this.behaviorType = behaviorType;
        this.properties = properties;
        this.description = description;
        this.order = order;
        this.stageType = stageType;
        this.pipelineMetadata = pipeLineMetaData;
        this.dataStore = dataStore;
    }

    /**
     * Gets the name of the behavior.
     *
     * @return Name of the behavior.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the behavior.
     *
     * @param name Name that is to be set.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the type of the behavior.
     *
     * @return Type of the behavior.
     */
    public BehaviorType getBehaviorType() {
        return behaviorType;
    }

    /**
     * Sets the type of the behavior.
     *
     * @param behaviorType Type of the behavior.
     */
    public void setBehaviorType(BehaviorType behaviorType) {
        this.behaviorType = behaviorType;
    }

    /**
     * Gets the order of the behavior.
     *
     * @return Order of the behavior.
     */
    public int getOrder() {
        return order;
    }

    /**
     * Sets the order in which the behavior will be executed.
     *
     * @param order Order to be set
     */
    public void setOrder(int order) {
        this.order = order;
    }

    /**
     * Gets the stage type of the behavior.
     *
     * @return Stage type of the behavior.
     */
    public StageType getStageType() {
        return stageType;
    }

    /**
     * Sets the stage type of the behavior.
     *
     * @param stageType Stage type that is to be set.
     */
    public void setStageType(StageType stageType) {
        this.stageType = stageType;
    }

    /**
     * Gets the Process Metadata Document that the behavior is associated with.
     *
     * @return Process Metadata Document that the behavior is associated with.
     */
    public PipelineMetadata getPipelineMetadata() {
        return pipelineMetadata;
    }

    /**
     * Sets the Process Metadata Document that the behavior is associated with.
     *
     * @param pipelineMetadata Process Metadata Document that the behavior is associated with.
     */
    public void setPipelineMetadata(PipelineMetadata pipelineMetadata) {
        this.pipelineMetadata = pipelineMetadata;
    }

    /**
     * Gets the DataStore representing the data.
     *
     * @return DataStore on which the staged behavior operates.
     */
    public DataStoreInterface getDataStore() {
        return dataStore;
    }

    /**
     * Sets the DataStore representing the data.
     *
     * @param dataStore DataStore that is to be set.
     */
    public void setDataStore(DataStoreInterface dataStore) {
        this.dataStore = dataStore;
    }


    /**
     * Gets description of the behavior.
     *
     * @return Description of the behavior.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets description of the behavior.
     *
     * @param description Description to be set.
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Gets the properties of the behavior.
     *
     * @return Properties of the behavior.
     */
    public Map<String, String> getProperties() {
        return properties;
    }


    /**
     * Sets the properties of the behavior.
     *
     * @param properties Properties that are to be set.
     */
    public void setProperties(HashMap<String, String> properties) {

        this.properties = properties;
    }
}
