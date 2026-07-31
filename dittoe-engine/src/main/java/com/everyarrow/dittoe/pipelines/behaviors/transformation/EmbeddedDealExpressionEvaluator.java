package com.everyarrow.dittoe.pipelines.behaviors.transformation;

import com.everyarrow.dittoe.pipelines.behaviors.transformation.expression.Expression;

import java.util.Set;

/**
 * Compatibility evaluator for the current embedded DEAL implementation.
 * DEAL owns expression semantics; DITTOE keeps this adapter until DEAL is separated.
 */
public final class EmbeddedDealExpressionEvaluator implements ExpressionEvaluator {
    @Override
    public Object evaluate(String expression, ExpressionContext context) throws ExpressionEvaluationException {
        return compile(expression).evaluate(context);
    }

    @Override
    public CompiledExpression compile(String expression) throws ExpressionEvaluationException {
        final Expression parsedExpression;
        try {
            parsedExpression = Expression.parse(expression);
            parsedExpression.validate();
        } catch (DealCompileFailure e) {
            throw compilationException(e);
        } catch (RuntimeException e) {
            throw new ExpressionEvaluationException("Failed to compile DEAL expression: " + expression, e);
        }

        Set<String> referencedFields = Set.copyOf(parsedExpression.referencedFields());
        return new CompiledExpression() {
            @Override
            public Object evaluate(ExpressionContext context) throws ExpressionEvaluationException {
                return evaluateParsedExpression(expression, parsedExpression, context);
            }

            @Override
            public Set<String> referencedFields() {
                return referencedFields;
            }

            @Override
            public ExpressionType inferType(DealSchema schema) {
                return parsedExpression.inferType(schema);
            }

            @Override
            public DealSourceSpan sourceSpan() {
                return parsedExpression.sourceSpan();
            }
        };
    }

    private Object evaluateParsedExpression(String source, Expression expression, ExpressionContext context)
            throws ExpressionEvaluationException {
        try {
            return expression.evaluate(context);
        } catch (RuntimeException e) {
            throw new ExpressionEvaluationException("Failed to evaluate DEAL expression: " + source, e);
        }
    }

    private static DealCompilationException compilationException(DealCompileFailure failure) {
        DealDiagnostic diagnostic = new DealDiagnostic(
                failure.code(), DealDiagnosticSeverity.ERROR, failure.getMessage(), failure.span(), null);
        return new DealCompilationException(diagnostic, failure);
    }
}
