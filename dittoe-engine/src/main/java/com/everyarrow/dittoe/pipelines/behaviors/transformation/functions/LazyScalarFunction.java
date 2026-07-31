package com.everyarrow.dittoe.pipelines.behaviors.transformation.functions;

/** A DEAL function that decides which arguments need to be evaluated. */
@FunctionalInterface
public interface LazyScalarFunction {
    Object apply(LazyArguments arguments);
}
