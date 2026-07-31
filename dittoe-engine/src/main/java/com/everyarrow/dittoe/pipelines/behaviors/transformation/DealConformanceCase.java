package com.everyarrow.dittoe.pipelines.behaviors.transformation;

/** A portable DEAL expression/result fixture shared with editor consumers. */
public record DealConformanceCase(String name, String expression, Object expected) {
}
