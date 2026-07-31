package com.everyarrow.dittoe.pipelines.execution.abstraction;

/**
 * It is the top level exception that is thrown if any error occurs during the execution of the Pipeline.
 * It is a checked exception, therefore, handling it is necessary.
 */
public class ExecutionException extends Exception {

    /**
     * Constructor.
     */
    public ExecutionException() {
        super();
    }

    /**
     * Constructor.
     *
     * @param message Message of the exception.
     */
    public ExecutionException(String message) {
        super(message);
    }

    /**
     * Constructor.
     *
     * @param cause Cause of the exception.
     */
    public ExecutionException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructor.
     *
     * @param message Message of the exception.
     * @param cause   Cause of the exception.
     */
    public ExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Converts the exception object into a String.
     *
     * @return String representation of the exception.
     */
    @Override
    public String toString() {
        if (this.getMessage() == null)
            return "ExecutionException";
        return "ExecutionException { message = " + this.getMessage() + " }";
    }
}
