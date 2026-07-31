package com.everyarrow.dittoe.pipelines.behaviors.transformation.functions;

/** Lazily evaluated, memoized arguments supplied to short-circuiting DEAL functions. */
public interface LazyArguments {
    int size();

    Object get(int index);
}
