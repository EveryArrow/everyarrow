package com.everyarrow.dittoe.pipelines.behaviors.transformation;

public interface ExpressionEvaluator {
    Object evaluate(String expression, ExpressionContext context) throws ExpressionEvaluationException;

    default CompiledExpression compile(String expression) throws ExpressionEvaluationException {
        return context -> evaluate(expression, context);
    }
}
