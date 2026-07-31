package com.everyarrow.dittoe.io;

import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import com.everyarrow.dittoe.utility.retry.Retry;
import com.everyarrow.dittoe.utility.retry.RetryType;

/**
 * Abstract base class representing a strategy for writing data to a resource.
 * <p>
 * The {@code WriteStrategy} class defines the contract for performing write operations
 * and incorporates a retry mechanism to handle transient failures.
 * Concrete implementations of this class must provide an implementation for the {@link #write()} method
 * which executes the actual data writing logic.
 * <p>
 * In addition to the write operation, this class allows the association of a {@link Writer}
 * that utilizes this strategy.
 */
public abstract class WriteStrategy {
    /**
     * The retry mechanism used to reattempt the write operation in case of transient failures.
     * <p>
     * This mechanism is initialized using the default retry configuration defined by {@link RetryType}.
     */
    private final Retry retry;

    /**
     * The {@link Writer} that utilizes this write strategy to perform write operations.
     */
    private Writer writer;

    /**
     * Constructs a new {@code WriteStrategy} instance and initializes the retry mechanism.
     * <p>
     * This constructor uses reflection to instantiate the default retry mechanism as defined by
     * {@link RetryType}. If the instantiation fails, an {@link ExecutionException} is thrown.
     *
     * @throws ExecutionException if the retry mechanism cannot be initialized.
     */
    public WriteStrategy() throws ExecutionException {
        try {
            // Instantiate the default retry mechanism via reflection.
            this.retry = RetryType.getDefault().classReference().getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new ExecutionException("Failed to initialize retry mechanism", e);
        }
    }

    /**
     * Initializes the write strategy.
     * <p>
     * Concrete subclasses may override this method to perform any necessary initialization
     * steps prior to executing the write operation.
     *
     * @throws ExecutionException if initialization fails.
     */
    public void init() throws ExecutionException {
        // Default implementation does nothing.
        // Subclasses may override this method to perform strategy-specific initialization.
    }

    /**
     * Retrieves the retry mechanism associated with this write strategy.
     *
     * @return the {@link Retry} instance used for executing the write operation with retry support.
     */
    public Retry getRetry() {
        return retry;
    }

    /**
     * Retrieves the {@link Writer} associated with this write strategy.
     *
     * @return the writer instance that uses this strategy.
     */
    public Writer getWriter() {
        return writer;
    }

    /**
     * Associates a {@link Writer} with this write strategy.
     *
     * @param writer the writer to be associated with this strategy.
     */
    public void setWriter(Writer writer) {
        this.writer = writer;
    }

    /**
     * Executes the write operation.
     * <p>
     * Concrete subclasses must implement this method to perform the actual write operation,
     * which may involve writing data to a resource, file, database, or other medium.
     * <p>
     * The write operation should be executed within a retry mechanism provided by {@link #getRetry()},
     * ensuring that transient failures are handled gracefully.
     *
     * @throws ExecutionException if an error occurs during the write operation.
     */
    public abstract void write() throws ExecutionException;
}
