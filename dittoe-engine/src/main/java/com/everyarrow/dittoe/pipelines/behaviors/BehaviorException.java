package com.everyarrow.dittoe.pipelines.behaviors;

import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

/**
 * This exception is thrown by Behaviors in case of any problem.
 */
public class BehaviorException extends ExecutionException {

    /**
     * Constructor.
     */
    public BehaviorException() {
        super();
    }

    /**
     * Constructor.
     *
     * @param message Message of the exception.
     */
    public BehaviorException(String message) {
        super(message);
    }

    /**
     * Constructor.
     *
     * @param cause Cause of the exception.
     */
    public BehaviorException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructor.
     *
     * @param message Message of the exception.
     * @param cause   Cause of the exception.
     */
    public BehaviorException(String message, Throwable cause) {
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
            return "BehaviorException";
        return "BehaviorException { message = " + this.getMessage() + " }";
    }
}
