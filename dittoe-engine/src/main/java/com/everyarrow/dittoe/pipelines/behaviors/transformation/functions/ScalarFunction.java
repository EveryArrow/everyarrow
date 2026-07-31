package com.everyarrow.dittoe.pipelines.behaviors.transformation.functions;

/**
 * Represents a generic scalar function that takes an array of arguments and returns a result.
 */
@FunctionalInterface
public interface ScalarFunction {
    /**
     * Applies the function to the provided arguments.
     *
     * @param args The function arguments.
     * @return The result of the function.
     */
    Object apply(Object[] args);
}
