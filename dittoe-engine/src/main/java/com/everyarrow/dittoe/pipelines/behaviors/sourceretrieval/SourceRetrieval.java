package com.everyarrow.dittoe.pipelines.behaviors.sourceretrieval;

import com.everyarrow.dittoe.io.IOServer;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.pipelines.behaviors.StagedBehaviorBase;
import com.everyarrow.dittoe.pipelines.behaviors.StagedBehaviorInput;
import com.everyarrow.dittoe.pipelines.execution.abstraction.BehaviorException;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import com.everyarrow.dittoe.utility.DataReadObserver;
import org.everyarrow.dittoe.schema.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Implementation of SourceRetrieval behavior. It is a staged behavior. This behavior is used to read data from
 * a resource. Its StageType is always acquire and Order is always the minimum integer value.
 */
public class SourceRetrieval extends StagedBehaviorBase {

    private final Logger logger = LoggerFactory.getLogger(SourceRetrieval.class);
    private final List<DataReadObserver> dataReadObservers;

    /**
     * Constructor.
     *
     * @param name             Name of the behavior.
     * @param description      Description of the behavior.
     * @param properties       Properties of the behavior.
     * @param pipelineMetadata Process Metadata Document to which the behavior belongs.
     * @param dataStore        DataStore on which behavior operates.
     */
    public SourceRetrieval(String name, String description, Map<String, String> properties,
                           PipelineMetadata pipelineMetadata, DataStoreInterface dataStore) {
        super(new StagedBehaviorInput(name, BehaviorType.SourceRetrieval, properties, description,
                Integer.MIN_VALUE, StageType.Acquire, pipelineMetadata, dataStore));
        this.dataReadObservers = new LinkedList<>();
    }

    /**
     * Method contains the logic of the execution of the behavior.
     *
     * @throws BehaviorException Thrown if any error occurs during the behavior's executions.
     */
    @Override
    public void exec() throws BehaviorException {
        IOServer ioServer = (IOServer) this.getPipelineMetadata().getSourceResourceToProcess().getServer();

        try {
            // Add observers to the reader
            this.getDataReadObservers().forEach(observer ->
                    ioServer.getReader().addDataReadObserver(observer));
            
            // Initialize error count threshold from behavior properties
            String errorCountThresholdStr = this.getProperties().get("errorCountThreshold");
            if (errorCountThresholdStr != null && !errorCountThresholdStr.trim().isEmpty()) {
                try {
                    int errorCountThreshold = Integer.parseInt(errorCountThresholdStr.trim());
                    if (errorCountThreshold <= 0) {
                        throw new ExecutionException("errorCountThreshold must be a positive integer, got: " + errorCountThresholdStr);
                    }
                    // Set threshold on all registered observers
                    ioServer.getReader().getDataReadObservers().forEach(observer -> observer.setErrorCountThreshold(errorCountThreshold));
                } catch (NumberFormatException e) {
                    throw new ExecutionException("Invalid errorCountThreshold value: " + errorCountThresholdStr + ". Must be a valid integer.", e);
                }
            }
            
            int limit = Integer.parseInt(this.getPipelineMetadata().getSourceResourceToProcess().getResourceProperties().getOrDefault("limit", "0"));

            if (limit > 0) {
                ioServer.getReader().read(limit, getStageStats());
            } else {
                ioServer.getReader().read(getStageStats());
            }
            
            // Update stage stats from observers
            dataReadObservers.forEach(observer -> {
                getStageStats().setRowsProcessed(observer.getRowsRead());
                getStageStats().setRowsErrored(observer.getRowsErrored());
            });
            
            // If no observers, use dataStore size
            if (dataReadObservers.isEmpty()) {
                getStageStats().setRowsProcessed(getDataStore().size());
            }
        } catch (ExecutionException e) {
            getStageStats().incrementRowsErrored(getDataStore().size());
            throw new BehaviorException(e);
        }
    }

    public List<DataReadObserver> getDataReadObservers() {
        return dataReadObservers;
    }

    public Logger getLogger() {
        return logger;
    }
}
