package com.everyarrow.dittoe.io;

import com.everyarrow.dittoe.pipelines.execution.PipelineExecutionStats;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.Field;
import com.everyarrow.dittoe.utility.retry.Retry;
import com.everyarrow.dittoe.utility.retry.RetryType;

import java.util.List;

/**
 * Abstract base class representing a generic read strategy for retrieving data from a resource.
 * <p>
 * A read strategy defines the blueprint for various read operations, including:
 * <ul>
 *   <li>Reading data from the resource.</li>
 *   <li>Retrieving a range of data identifiers.</li>
 *   <li>Counting the total number of data records.</li>
 *   <li>Calculating the average row size of the data.</li>
 *   <li>Fetching metadata fields that describe the structure of the data.</li>
 * </ul>
 * <p>
 * This class integrates a retry mechanism to allow operations to be reattempted in the event of transient failures.
 * Concrete subclasses must provide implementations for the abstract methods defined below.
 */
public abstract class ReadStrategy {
    /**
     * The retry mechanism used to reattempt operations when failures occur.
     */
    private final Retry retry;

    /**
     * The {@link Reader} associated with this strategy.
     * <p>
     * The reader provides context and configuration (such as the data source and resource details)
     * needed for the strategy to perform its operations.
     */
    private Reader reader;
    
    /**
     * The stage statistics for tracking rows processed and errored.
     */
    private PipelineExecutionStats.StageStats stageStats;

    /**
     * Protected constructor that initializes the retry mechanism using the default retry configuration.
     * <p>
     * This constructor uses reflection to instantiate the default retry mechanism defined in {@link RetryType}.
     *
     * @throws ExecutionException if the retry mechanism cannot be initialized.
     */
    protected ReadStrategy() throws ExecutionException {
        try {
            // Instantiate the default retry mechanism via reflection.
            this.retry = RetryType.getDefault().classReference().getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new ExecutionException("Failed to initialize retry mechanism", e);
        }
    }

    /**
     * Initializes the read strategy.
     * <p>
     * Concrete subclasses may override this method to perform any necessary initialization logic
     * specific to the read strategy.
     *
     * @throws ExecutionException if initialization fails.
     */
    public void init() throws ExecutionException {
        // Default implementation does nothing.
        // Subclasses can override this method to add initialization logic.
    }

    /**
     * Retrieves the retry mechanism associated with this read strategy.
     *
     * @return the {@link Retry} instance used for executing operations with retry support.
     */
    public Retry getRetry() {
        return retry;
    }

    /**
     * Retrieves the {@link Reader} associated with this read strategy.
     *
     * @return the reader that provides context for the strategy.
     */
    public Reader getReader() {
        return reader;
    }

    /**
     * Associates a {@link Reader} with this read strategy.
     *
     * @param reader the reader to be associated with the strategy.
     */
    public void setReader(Reader reader) {
        this.reader = reader;
    }
    
    /**
     * Gets the stage statistics.
     *
     * @return the stage statistics
     */
    public PipelineExecutionStats.StageStats getStageStats() {
        return stageStats;
    }
    
    /**
     * Sets the stage statistics.
     *
     * @param stageStats the stage statistics to set
     */
    public void setStageStats(PipelineExecutionStats.StageStats stageStats) {
        this.stageStats = stageStats;
    }

    /**
     * Reads data from the resource.
     * <p>
     * Concrete subclasses must implement this method to perform the actual data reading operation.
     *
     * @throws ExecutionException if an error occurs during the reading process.
     */
    public abstract void read() throws ExecutionException;


    public abstract void read(int limit) throws ExecutionException;

    /**
     * Retrieves a range of data identifiers from the resource.
     * <p>
     * Concrete subclasses must implement this method to return a list of {@code Long} values representing
     * the range of data available.
     *
     * @return a list of {@code Long} values defining the range of data.
     * @throws ExecutionException if an error occurs while retrieving the range.
     */
    public abstract List<Long> getRange() throws ExecutionException;

    /**
     * Retrieves the total count of data records from the resource.
     * <p>
     * Concrete subclasses must implement this method to return the number of data records available.
     *
     * @return the total count of data records as a {@code long} value.
     * @throws ExecutionException if an error occurs while retrieving the data count.
     */
    public abstract long getDataCount() throws ExecutionException;

    /**
     * Calculates the average size of a data row.
     * <p>
     * Concrete subclasses must implement this method to compute and return the average row size.
     *
     * @return the average row size as an {@code int}.
     * @throws ExecutionException if an error occurs while calculating the average row size.
     */
    public abstract int getAverageRowSize() throws ExecutionException;

    /**
     * Retrieves metadata fields describing the structure of the data resource.
     * <p>
     * Concrete subclasses must implement this method to return a list of {@link Field} objects that
     * contain metadata information about the data.
     *
     * @return a list of {@link Field} objects representing metadata fields.
     * @throws ExecutionException if an error occurs while retrieving the metadata fields.
     */
    public abstract List<Field> getFields() throws ExecutionException;
}
