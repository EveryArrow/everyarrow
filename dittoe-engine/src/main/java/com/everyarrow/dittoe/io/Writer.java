package com.everyarrow.dittoe.io;

import com.everyarrow.utilities.reflection.Reflection;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.pipelines.behaviors.instrumentation.Instrumentation;
import com.everyarrow.dittoe.pipelines.execution.PipelineExecutionStats;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.PipelineMetadata;
import com.everyarrow.dittoe.utility.DataInsertionObserver;
import com.everyarrow.dittoe.utility.metadata.MetadataRepositoryAPI;
import com.everyarrow.dittoe.utility.retry.ExponentialBackOff;
import com.everyarrow.dittoe.utility.retry.Retry;
import com.everyarrow.dittoe.utility.retry.RetryType;
import com.everyarrow.dittoe.utility.security.PluginClassSafety;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;

/**
 * Abstract class representing a generic Writer responsible for writing data to a resource.
 * <p>
 * The {@code Writer} class utilizes a pluggable {@link WriteStrategy} to perform the actual writing
 * operations. It also incorporates a retry mechanism to handle transient failures and ensures that
 * necessary thread-local contexts (such as data store, pipeline metadata, and instrumentation) are properly
 * set before executing any write operation.
 * <p>
 * Additionally, the class maintains a list of {@link DataInsertionObserver} instances to notify observers
 * whenever new data is inserted.
 */
public abstract class Writer {
    /**
     * Logger instance for logging runtime events, debug information, and errors.
     */
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * The retry mechanism used to reattempt operations in case of transient failures.
     */
    private final Retry retry;

    /**
     * List of observers to be notified when data is inserted.
     */
    private final List<DataInsertionObserver> dataInsertionObservers = new LinkedList<>();

    /**
     * The write strategy used to perform the write operation.
     * <p>
     * This strategy encapsulates the actual implementation logic for writing data.
     */
    private WriteStrategy writeStrategy;

    /**
     * The associated I/O server which provides context such as resource configuration.
     */
    private IOServer server;

    /**
     * Constructs a new {@code Writer} instance and initializes the retry mechanism.
     * <p>
     * The constructor uses reflection to instantiate the default retry mechanism defined by {@link RetryType}.
     * If instantiation fails, an {@link ExecutionException} is thrown.
     *
     * @throws ExecutionException if the retry mechanism cannot be initialized.
     */
    public Writer() throws ExecutionException {
        try {
            // Instantiate the default retry mechanism via reflection.
            this.retry = RetryType.getDefault().classReference().getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new ExecutionException("Failed to initialize retry mechanism", e);
        }
    }

    /**
     * Initializes the {@code Writer} by resolving and setting the appropriate write strategy.
     * <p>
     * This method retrieves the write strategy class name from the resource configuration of the associated
     * {@link IOServer}, logs the chosen strategy, instantiates it using reflection, and associates this writer
     * instance with the strategy.
     *
     * @throws ExecutionException if strategy resolution or initialization fails.
     */
    public void init() throws ExecutionException {
        // Retrieve the strategy class name from the server's resource configuration.
        String strategy = this.getServer().getResource().getStrategy();
        this.getLogger().info("Using write strategy: {}", strategy);

        // Instantiate the write strategy using reflection.
        String allowedStrategy = PluginClassSafety.requireAllowedEngineClass(strategy,
                this.getServer().getResource().getResourceProperties());
        this.setWriteStrategy((WriteStrategy) Reflection.instantiateClass(allowedStrategy));

        // Set the writer instance in the write strategy for callback/reference.
        this.getWriteStrategy().setWriter(this);
    }

    /**
     * Executes the write operation using the configured write strategy.
     * <p>
     * This method sets up the necessary thread-local context (data store, pipeline metadata, instrumentation)
     * and delegates the actual writing operation to the write strategy within a retry-enabled execution.
     *
     * @throws ExecutionException if any error occurs during the write operation.
     * @throws RuntimeException   if the underlying write strategy execution fails.
     */
    public void write() throws ExecutionException {
        // Preserve the current thread-local settings.
        DataStoreInterface dataStoreInterface = DataStoreInterface.getDataStoreInterface();
        PipelineMetadata pipelineMetadata = MetadataRepositoryAPI.getPipelineMetadata();
        String pipelineMetadataRepositoryAPIKey = MetadataRepositoryAPI.getApiKey();
        String instrumentationAPIKey = Instrumentation.getApiKey();
        Instrumentation instrumentation = Instrumentation.getInstance();

        try {
            // Execute the write operation within a retry context.
            this.getRetry().execute(() -> {
                // Set thread-local context for the write operation.
                DataStoreInterface.setDataStoreInterface(dataStoreInterface);
                MetadataRepositoryAPI.setPipelineMetadata(pipelineMetadata);
                MetadataRepositoryAPI.setApiKey(pipelineMetadataRepositoryAPIKey);
                Instrumentation.setInstance(instrumentation);
                Instrumentation.setApiKey(instrumentationAPIKey);

                // Delegate to the write strategy to perform the actual write.
                this.getWriteStrategy().write();
                return null;
            });
        } catch (Exception e) {
            // Wrap and propagate exceptions as a runtime exception.
            throw new RuntimeException(e);
        }
    }

    /**
     * Retrieves the {@link IOServer} associated with this writer.
     *
     * @return the {@code IOServer} instance.
     */
    public IOServer getServer() {
        return server;
    }

    /**
     * Sets the {@link IOServer} for this writer.
     *
     * @param server the {@code IOServer} that provides context such as resource configuration.
     */
    public void setServer(IOServer server) {
        this.server = server;
    }

    /**
     * Retrieves the write strategy used by this writer.
     *
     * @return the {@link WriteStrategy} instance.
     */
    public WriteStrategy getWriteStrategy() {
        return writeStrategy;
    }

    /**
     * Sets the write strategy for this writer.
     *
     * @param writeStrategy the {@link WriteStrategy} to be used for write operations.
     */
    public void setWriteStrategy(WriteStrategy writeStrategy) {
        this.writeStrategy = writeStrategy;
    }

    /**
     * Retrieves the retry mechanism associated with this writer.
     *
     * @return the {@link Retry} instance used for retrying operations.
     */
    public Retry getRetry() {
        return retry;
    }

    /**
     * Retrieves the list of data insertion observers.
     * <p>
     * This method is package-private to allow write strategies to access observers.
     *
     * @return a {@code List} of {@link DataInsertionObserver} instances.
     */
    public List<DataInsertionObserver> getDataInsertionObservers() {
        return dataInsertionObservers;
    }

    /**
     * Adds a {@link DataInsertionObserver} to be notified when new data is inserted.
     *
     * @param observer the observer to add.
     */
    public void addDataInsertionObserver(DataInsertionObserver observer) {
        this.getDataInsertionObservers().add(observer);
    }

    /**
     * Removes a {@link DataInsertionObserver} from the notification list.
     *
     * @param observer the observer to remove.
     */
    public void removeDataInsertionObserver(DataInsertionObserver observer) {
        this.getDataInsertionObservers().remove(observer);
    }

    /**
     * Notifies all registered {@link DataInsertionObserver} instances that new data has been inserted.
     * <p>
     * Each observer's {@code recordInserted} method is invoked.
     */
    public void notifyDataInsertionObservers() {
        this.getDataInsertionObservers().forEach(DataInsertionObserver::recordInserted);
    }

    /**
     * Notifies all registered {@link DataInsertionObserver} instances that new data has been inserted.
     * <p>
     * Each observer's {@code recordInserted} method is invoked with the specified count.
     *
     * @param count the number of rows inserted.
     */
    public void notifyDataInsertionObservers(long count) {
        this.getDataInsertionObservers().forEach(observer -> observer.recordInserted(count));
    }

    /**
     * Notifies all registered {@link DataInsertionObserver} instances that an error occurred.
     * <p>
     * Each observer's {@code recordErrored} method is invoked, which will check the error threshold.
     *
     * @throws ExecutionException if the error count threshold is exceeded.
     */
    public void notifyDataInsertionObserversError() throws ExecutionException {
        for (DataInsertionObserver observer : this.getDataInsertionObservers()) {
            observer.recordErrored();
        }
    }

    /**
     * Retrieves the logger for this writer.
     *
     * @return the {@link Logger} instance used for logging.
     */
    public Logger getLogger() {
        return logger;
    }

    /**
     * Sets the pipeline execution stats for retry tracking.
     *
     * @param stats the {@link PipelineExecutionStats} instance.
     */
    public void setStats(PipelineExecutionStats stats) {
        if (this.retry instanceof ExponentialBackOff) {
            ((ExponentialBackOff) this.retry).setStats(stats);
        }
    }
}
