package com.everyarrow.dittoe.pipelines.behaviors.targetinsertupdate;

import com.everyarrow.dittoe.io.IOServer;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.pipelines.behaviors.StagedBehaviorBase;
import com.everyarrow.dittoe.pipelines.behaviors.StagedBehaviorInput;
import com.everyarrow.dittoe.pipelines.execution.PipelineExecutionStats;
import com.everyarrow.dittoe.pipelines.execution.abstraction.BehaviorException;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.BehaviorType;
import org.everyarrow.dittoe.schema.PipelineMetadata;
import org.everyarrow.dittoe.schema.StageType;
import com.everyarrow.dittoe.utility.DataInsertionObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Implementation of TargetInsertUpdate behavior. It is a staged behavior. This behavior is used to write
 * data to a resource. Its StageType is always Transmit and Order is always the maximum integer value.
 */
public class TargetInsertUpdate extends StagedBehaviorBase {

    private final Logger logger = LoggerFactory.getLogger(TargetInsertUpdate.class);

    private final List<DataInsertionObserver> dataInsertionObservers;
    private PipelineExecutionStats stats;

    /**
     * Constructor.
     *
     * @param name             Name of the behavior.
     * @param description      Description of the behavior.
     * @param properties       Properties of the behavior.
     * @param pipelineMetadata Process Metadata Document to which the behavior belongs.
     * @param dataStore        DataStore on which behavior operates.
     */
    public TargetInsertUpdate(String name, String description, Map<String, String> properties,
                              PipelineMetadata pipelineMetadata, DataStoreInterface dataStore) {
        super(new StagedBehaviorInput(name, BehaviorType.TargetInsertUpdate, properties, description,
                Integer.MAX_VALUE, StageType.Transmit, pipelineMetadata, dataStore));
        this.dataInsertionObservers = new LinkedList<>();
    }

    /**
     * Method contains the logic of the execution of the behavior.
     *
     * @throws BehaviorException Thrown if any error occurs during the behavior's executions.
     */
    @Override
    public void exec() throws BehaviorException {
        try {
            IOServer ioServer = (IOServer) this.getPipelineMetadata().getTargetResourceToProcess().getServer();
            this.getDataInsertionObservers().forEach(dataInsertionObservers ->
                    ioServer.getWriter().addDataInsertionObserver(dataInsertionObservers));

            // Initialize error count threshold from resource properties
            String errorCountThresholdStr = this.getProperties().get("errorCountThreshold");
            if (errorCountThresholdStr != null && !errorCountThresholdStr.trim().isEmpty()) {
                try {
                    int errorCountThreshold = Integer.parseInt(errorCountThresholdStr.trim());
                    if (errorCountThreshold <= 0) {
                        throw new ExecutionException("errorCountThreshold must be a positive integer, got: " + errorCountThresholdStr);
                    }
                    // Set threshold on all registered observers
                    ioServer.getWriter().getDataInsertionObservers().forEach(observer -> observer.setErrorCountThreshold(errorCountThreshold));
                } catch (NumberFormatException e) {
                    throw new ExecutionException("Invalid errorCountThreshold value: " + errorCountThresholdStr + ". Must be a valid integer.", e);
                }
            }

                ioServer.getWriter().setStats(stats);

                ioServer.getWriter().write();

            dataInsertionObservers.forEach(observer -> {
                getStageStats().setRowsProcessed(observer.getRowsWritten());
                getStageStats().setRowsErrored(observer.getRowsErrored());
            });
        } catch (Exception e) {
            throw new BehaviorException(e);
        }
    }

    public List<DataInsertionObserver> getDataInsertionObservers() {
        return dataInsertionObservers;
    }

    public Logger getLogger() {
        return logger;
    }

    public void setStats(PipelineExecutionStats stats) {
        this.stats = stats;
    }
}