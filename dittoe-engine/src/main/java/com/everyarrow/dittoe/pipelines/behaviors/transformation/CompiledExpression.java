package com.everyarrow.dittoe.pipelines.behaviors.transformation;

import java.util.Set;

/**
 * A DEAL expression that has already been parsed and is ready for row evaluation.
 */
@FunctionalInterface
public interface CompiledExpression {
    Object evaluate(ExpressionContext context) throws ExpressionEvaluationException;

    default Set<String> referencedFields() {
        return Set.of();
    }

    /** Infers this expression against the PMD Resource schema before row evaluation. */
    default ExpressionType inferType(DealSchema schema) {
        return ExpressionType.any();
    }

    default DealSourceSpan sourceSpan() {
        return DealSourceSpan.unknown();
    }
}
