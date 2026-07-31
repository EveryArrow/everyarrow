package com.everyarrow.dittoe.io;

import com.everyarrow.utilities.reflection.Reflection;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.pipelines.behaviors.instrumentation.Instrumentation;
import com.everyarrow.dittoe.pipelines.execution.PipelineExecutionStats;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.Field;
import org.everyarrow.dittoe.schema.PipelineMetadata;
import com.everyarrow.dittoe.utility.DataReadObserver;
import com.everyarrow.dittoe.utility.metadata.MetadataRepositoryAPI;
import com.everyarrow.dittoe.utility.retry.Retry;
import com.everyarrow.dittoe.utility.retry.RetryType;
import com.everyarrow.dittoe.utility.security.PluginClassSafety;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;

/**
 * Abstract class representing a generic Reader, which uses a pluggable strategy to read data from a resource.
 * <p>
 * The {@code Reader} class is responsible for executing various read operations such as reading data, retrieving data ranges,
 * counting data records, obtaining average row sizes, and retrieving field metadata from a resource.
 * It leverages a retry mechanism to handle transient errors and to ensure robustness in the execution of these operations.
 * <p>
 * The class makes use of a {@link ReadStrategy} that encapsulates the actual implementation of the read logic.
 * Concrete subclasses should ensure that a proper read strategy is configured before executing any operations.
 */
public abstract class Reader {
    /**
     * Logger instance for logging runtime information and errors.
     */
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * Retry mechanism used to execute operations with automatic retries in case of failures.
     */
    private final Retry retry;

    /**
     * The strategy used to perform read operations.
     * <p>
     * The read strategy is dynamically determined at runtime based on the resource configuration.
     */
    private ReadStrategy readStrategy;

    /**
     * The associated I/O server that holds context information such as the resource configuration.
     */
    private IOServer server;
    
    /**
     * List of data read observers that track read operations.
     */
    private final List<DataReadObserver> dataReadObservers;

    /**
     * Constructs a new {@code Reader} instance and initializes the retry mechanism.
     * <p>
     * The constructor uses reflection to instantiate the default retry mechanism defined by {@link RetryType}.
     * If instantiation fails, an {@link ExecutionException} is thrown.
     *
     * @throws ExecutionException if the retry mechanism cannot be initialized.
     */
    public Reader() throws ExecutionException {
        try {
            // Instantiate the default retry mechanism via reflection.
            // RetryType.getDefault() returns the default retry configuration, from which the actual class is obtained.
            this.retry = RetryType.getDefault().classReference().getDeclaredConstructor().newInstance();
            this.dataReadObservers = new LinkedList<>();
        } catch (Exception e) {
            throw new ExecutionException("Failed to initialize retry mechanism", e);
        }
    }

    /**
     * Initializes the {@code Reader} by resolving and setting the appropriate read strategy.
     * <p>
     * This method retrieves the strategy class name from the resource configuration associated with the server,
     * logs the chosen strategy, instantiates the strategy using reflection, and associates this reader with the strategy.
     *
     * @throws ExecutionException if strategy resolution or initialization fails.
     */
    public void init() throws ExecutionException {
        // Retrieve the strategy configuration from the server's resource metadata.
        String strategy = this.getServer().getResource().getStrategy();
        this.getLogger().info("Using read strategy: {}", strategy);

        if (strategy == null)
            return;

        // Instantiate the read strategy class using reflection.
        String allowedStrategy = PluginClassSafety.requireAllowedEngineClass(strategy,
                this.getServer().getResource().getResourceProperties());
        this.setReadStrategy((ReadStrategy) Reflection.instantiateClass(allowedStrategy));
        // Set the current reader instance to the strategy for back-reference.
        this.getReadStrategy().setReader(this);
    }

    /**
     * Returns the currently configured read strategy.
     *
     * @return the {@link ReadStrategy} in use.
     */
    public ReadStrategy getReadStrategy() {
        return readStrategy;
    }

    /**
     * Sets the read strategy for this reader.
     *
     * @param readStrategy the {@link ReadStrategy} to be used for read operations.
     */
    public void setReadStrategy(ReadStrategy readStrategy) {
        this.readStrategy = readStrategy;
    }

    /**
     * Retrieves the associated I/O server.
     *
     * @return the {@link IOServer} instance.
     */
    public IOServer getServer() {
        return server;
    }

    /**
     * Sets the I/O server for this reader.
     *
     * @param server the {@link IOServer} that holds configuration and resource details.
     */
    public void setServer(IOServer server) {
        this.server = server;
    }

    /**
     * Reads data using the configured read strategy with validation enabled by default.
     * <p>
     * This method sets up the thread-local context for the data store, pipeline metadata, and instrumentation before
     * executing the read operation via the retry mechanism. Any exceptions during reading are wrapped in an
     * {@link ExecutionException}.
     *
     * @throws ExecutionException if any error occurs during the read operation.
     */
    public void read() throws ExecutionException {
        read(null);
    }
    
    /**
     * Reads data using the configured read strategy with stage statistics tracking.
     *
     * @param stageStats The stage statistics to track errors
     * @throws ExecutionException if any error occurs during the read operation.
     */
    public void read(PipelineExecutionStats.StageStats stageStats) throws ExecutionException {
        // Preserve the current thread-local settings for data store, metadata, and instrumentation.
        DataStoreInterface dataStoreInterface = DataStoreInterface.getDataStoreInterface();
        PipelineMetadata pipelineMetadata = MetadataRepositoryAPI.getPipelineMetadata();
        Instrumentation instrumentation = Instrumentation.getInstance();
        String pipelineMetadataRepositoryAPIKey = MetadataRepositoryAPI.getApiKey();
        String instrumentationAPIKey = Instrumentation.getApiKey();
        try {
            // Set stage stats in the read strategy
            this.getReadStrategy().setStageStats(stageStats);
            
            // Execute the read operation using the retry mechanism.
            this.getRetry().execute(() -> {
                // Restore thread-local context for the read operation.
                DataStoreInterface.setDataStoreInterface(dataStoreInterface);
                MetadataRepositoryAPI.setPipelineMetadata(pipelineMetadata);
                MetadataRepositoryAPI.setApiKey(pipelineMetadataRepositoryAPIKey);
                Instrumentation.setInstance(instrumentation);
                Instrumentation.setApiKey(instrumentationAPIKey);
                DataStoreInterface.getDataStoreInterface().clear();
                // Execute the actual read operation as defined by the read strategy.
                this.getReadStrategy().read();
                return null;
            });
        } catch (Exception exception) {
            throw new ExecutionException("Failed to read", exception);
        }
    }

    public void read(int limit) throws ExecutionException {
        read(limit, null);
    }
    
    public void read(int limit, PipelineExecutionStats.StageStats stageStats) throws ExecutionException {
        DataStoreInterface dataStoreInterface = DataStoreInterface.getDataStoreInterface();
        PipelineMetadata pipelineMetadata = MetadataRepositoryAPI.getPipelineMetadata();
        Instrumentation instrumentation = Instrumentation.getInstance();
        String pipelineMetadataRepositoryAPIKey = MetadataRepositoryAPI.getApiKey();
        String instrumentationAPIKey = Instrumentation.getApiKey();
        try {
            // Set stage stats in the read strategy
            this.getReadStrategy().setStageStats(stageStats);
            
            // Execute the read operation using the retry mechanism.
            this.getRetry().execute(() -> {
                // Restore thread-local context for the read operation.
                DataStoreInterface.setDataStoreInterface(dataStoreInterface);
                MetadataRepositoryAPI.setPipelineMetadata(pipelineMetadata);
                Instrumentation.setInstance(instrumentation);
                MetadataRepositoryAPI.setApiKey(pipelineMetadataRepositoryAPIKey);
                Instrumentation.setApiKey(instrumentationAPIKey);
                DataStoreInterface.getDataStoreInterface().clear();
                // Execute the actual read operation as defined by the read strategy.
                this.getReadStrategy().read(limit);
                return null;
            });
        } catch (Exception exception) {
            throw new ExecutionException("Failed to read", exception);
        }
    }

    /**
     * Retrieves a range of data identifiers from the resource using the read strategy.
     * <p>
     * Similar to {@link #read()}, this method sets up the thread-local context before delegating the call to
     * the read strategy's {@code getRange()} method. The result is a list of long values representing the range.
     *
     * @return a {@code List} of {@code Long} values defining the data range.
     * @throws ExecutionException if an error occurs while retrieving the range.
     */
    public List<Long> getRange() throws ExecutionException {
        DataStoreInterface dataStoreInterface = DataStoreInterface.getDataStoreInterface();
        PipelineMetadata pipelineMetadata = MetadataRepositoryAPI.getPipelineMetadata();
        Instrumentation instrumentation = Instrumentation.getInstance();
        String pipelineMetadataRepositoryAPIKey = MetadataRepositoryAPI.getApiKey();
        String instrumentationAPIKey = Instrumentation.getApiKey();
        try {
            return this.getRetry().execute(() -> {
                DataStoreInterface.setDataStoreInterface(dataStoreInterface);
                MetadataRepositoryAPI.setPipelineMetadata(pipelineMetadata);
                MetadataRepositoryAPI.setApiKey(pipelineMetadataRepositoryAPIKey);
                Instrumentation.setInstance(instrumentation);
                Instrumentation.setApiKey(instrumentationAPIKey);
                // Delegate the call to the read strategy to obtain the range.
                return this.getReadStrategy().getRange();
            });
        } catch (Exception exception) {
            throw new ExecutionException("Failed to get range", exception);
        }
    }

    /**
     * Retrieves the total count of data records available from the resource.
     * <p>
     * This method establishes the necessary thread-local context and executes the count retrieval operation via the retry mechanism.
     *
     * @return the count of data records as a {@code long} value.
     * @throws ExecutionException if an error occurs while counting the data records.
     */
    public long getDataCount() throws ExecutionException {
        DataStoreInterface dataStoreInterface = DataStoreInterface.getDataStoreInterface();
        PipelineMetadata pipelineMetadata = MetadataRepositoryAPI.getPipelineMetadata();
        Instrumentation instrumentation = Instrumentation.getInstance();
        String pipelineMetadataRepositoryAPIKey = MetadataRepositoryAPI.getApiKey();
        String instrumentationAPIKey = Instrumentation.getApiKey();
        try {
            return this.getRetry().execute(() -> {
                DataStoreInterface.setDataStoreInterface(dataStoreInterface);
                MetadataRepositoryAPI.setPipelineMetadata(pipelineMetadata);
                MetadataRepositoryAPI.setApiKey(pipelineMetadataRepositoryAPIKey);
                Instrumentation.setInstance(instrumentation);
                Instrumentation.setApiKey(instrumentationAPIKey);
                // Delegate to the read strategy to count the data.
                return this.getReadStrategy().getDataCount();
            });
        } catch (Exception exception) {
            throw new ExecutionException("Failed to get data count", exception);
        }
    }

    /**
     * Calculates the average size of rows in the data set.
     * <p>
     * Like other read operations, this method sets up the thread-local context and delegates the computation to
     * the read strategy within a retry-enabled execution.
     *
     * @return the average row size as an {@code int}.
     * @throws ExecutionException if an error occurs during the computation.
     */
    public int getAverageRowSize() throws ExecutionException {
        DataStoreInterface dataStoreInterface = DataStoreInterface.getDataStoreInterface();
        PipelineMetadata pipelineMetadata = MetadataRepositoryAPI.getPipelineMetadata();
        Instrumentation instrumentation = Instrumentation.getInstance();
        String pipelineMetadataRepositoryAPIKey = MetadataRepositoryAPI.getApiKey();
        String instrumentationAPIKey = Instrumentation.getApiKey();
        try {
            return this.getRetry().execute(() -> {
                DataStoreInterface.setDataStoreInterface(dataStoreInterface);
                MetadataRepositoryAPI.setPipelineMetadata(pipelineMetadata);
                MetadataRepositoryAPI.setApiKey(pipelineMetadataRepositoryAPIKey);
                Instrumentation.setInstance(instrumentation);
                Instrumentation.setApiKey(instrumentationAPIKey);
                // Delegate to the read strategy to calculate the average row size.
                return this.getReadStrategy().getAverageRowSize();
            });
        } catch (Exception exception) {
            throw new ExecutionException("Failed to get average row size", exception);
        }
    }

    /**
     * Retrieves metadata fields describing the structure of the data resource.
     * <p>
     * This method prepares the necessary thread-local context and delegates the retrieval of fields to
     * the read strategy within a retry-enabled operation.
     *
     * @return a {@code List} of {@link Field} objects representing metadata fields.
     * @throws ExecutionException if an error occurs while retrieving the fields.
     */
    public List<Field> getFields() throws ExecutionException {
        DataStoreInterface dataStoreInterface = DataStoreInterface.getDataStoreInterface();
        PipelineMetadata pipelineMetadata = MetadataRepositoryAPI.getPipelineMetadata();
        Instrumentation instrumentation = Instrumentation.getInstance();
        String pipelineMetadataRepositoryAPIKey = MetadataRepositoryAPI.getApiKey();
        String instrumentationAPIKey = Instrumentation.getApiKey();
        try {
            return this.getRetry().execute(() -> {
                DataStoreInterface.setDataStoreInterface(dataStoreInterface);
                MetadataRepositoryAPI.setPipelineMetadata(pipelineMetadata);
                MetadataRepositoryAPI.setApiKey(pipelineMetadataRepositoryAPIKey);
                Instrumentation.setInstance(instrumentation);
                Instrumentation.setApiKey(instrumentationAPIKey);
                // Delegate to the read strategy to retrieve field metadata.
                return this.getReadStrategy().getFields();
            });
        } catch (Exception exception) {
            throw new ExecutionException("Failed to get fields", exception);
        }
    }

    /**
     * Retrieves the retry mechanism associated with this reader.
     *
     * @return the {@link Retry} instance used for executing operations with retry support.
     */
    public Retry getRetry() {
        return retry;
    }

    /**
     * Retrieves the logger for this reader.
     *
     * @return the {@link Logger} instance.
     */
    public Logger getLogger() {
        return logger;
    }
    
    /**
     * Adds a data read observer to track read operations.
     *
     * @param observer the {@link DataReadObserver} to add
     */
    public void addDataReadObserver(DataReadObserver observer) {
        this.dataReadObservers.add(observer);
    }
    
    /**
     * Retrieves the list of data read observers.
     *
     * @return the list of {@link DataReadObserver} instances
     */
    public List<DataReadObserver> getDataReadObservers() {
        return dataReadObservers;
    }
}
