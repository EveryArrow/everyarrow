package com.everyarrow.dittoe.utility.metadata;


/**
 * It is the exception that is thrown if any error occurs while retrieving Process Metadata Document from a
 * repository.
 */
public class MetadataRepositoryException extends Exception {

    /**
     * Constructor.
     */
    public MetadataRepositoryException() {
    }

    /**
     * Constructor.
     *
     * @param message Message of the exception.
     */
    public MetadataRepositoryException(String message) {
        super(message);
    }

    /**
     * Constructor.
     *
     * @param cause Cause of the exception.
     */
    public MetadataRepositoryException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructor.
     *
     * @param message Message of the exception.
     * @param cause   Cause of the exception.
     */
    public MetadataRepositoryException(String message, Throwable cause) {
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
            return "MetadataRepositoryException";
        return "MetadataRepositoryException { message = " + this.getMessage() + " }";
    }
}
