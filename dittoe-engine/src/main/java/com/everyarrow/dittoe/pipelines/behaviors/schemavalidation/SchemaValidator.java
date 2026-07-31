package com.everyarrow.dittoe.pipelines.behaviors.schemavalidation;

import com.everyarrow.dittoe.io.data.Row;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

/**
 * Interface for SchemaValidator.
 */
public interface SchemaValidator {

    /**
     * Validates all the data.
     *
     * @throws ExecutionException Thrown if any error occurs during validation.
     */
    void validate() throws ExecutionException;

    /**
     * Validates a single row.
     *
     * @param row Row to be validated.
     * @return True if the row is valid else false.
     * @throws ExecutionException Thrown if any error occurs during validation.
     */
    boolean validate(Row row) throws ExecutionException;
}
