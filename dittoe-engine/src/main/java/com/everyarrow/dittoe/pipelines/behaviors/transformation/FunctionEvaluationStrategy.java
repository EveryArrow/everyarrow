package com.everyarrow.dittoe.pipelines.behaviors.transformation;

/** Controls whether a DEAL function receives evaluated values or lazy arguments. */
public enum FunctionEvaluationStrategy {
    EAGER,
    LAZY
}
