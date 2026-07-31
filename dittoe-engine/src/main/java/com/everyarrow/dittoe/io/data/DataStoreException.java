package com.everyarrow.dittoe.io.data;

//import com.dittoe.abstraction.execution.pipelines.dittoe.ExecutionException;

/**
 * This exception is thrown by DataStore in case of any problem.
 */
public class DataStoreException extends Exception {


    /**
     * Constructor.
     *
     * @param message Message of the exception.
     */
    public DataStoreException(String message) {
        super(message);
    }

    /**
     * Constructor.
     *
     * @param cause Cause of the exception.
     */
    public DataStoreException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructor.
     *
     * @param message Message of the exception.
     * @param cause   Cause of the exception.
     */
    public DataStoreException(String message, Throwable cause) {
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
            return "DataStoreException";
        return "DataStoreException { message = " + this.getMessage() + " }";
    }
}
