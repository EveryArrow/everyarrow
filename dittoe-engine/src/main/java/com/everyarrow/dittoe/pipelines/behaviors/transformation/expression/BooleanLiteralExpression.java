package com.everyarrow.dittoe.pipelines.behaviors.transformation.expression;

import com.everyarrow.dittoe.pipelines.behaviors.transformation.ExpressionContext;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.DealSchema;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.ExpressionType;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.DealSourceSpan;

/**
 * Represents a boolean literal expression (true or false).
 */
public class BooleanLiteralExpression implements Expression {
    private final boolean value;
    private final DealSourceSpan sourceSpan;

    public BooleanLiteralExpression(boolean value) {
        this(value, DealSourceSpan.unknown());
    }

    public BooleanLiteralExpression(boolean value, DealSourceSpan sourceSpan) {
        this.value = value;
        this.sourceSpan = sourceSpan;
    }

    @Override
    public Object evaluate(ExpressionContext context) {
        return value;
    }

    @Override
    public ExpressionType inferType(DealSchema schema) {
        return new ExpressionType(ExpressionType.Kind.BOOLEAN, false);
    }

    @Override
    public DealSourceSpan sourceSpan() {
        return sourceSpan;
    }

    @Override
    public String toString() {
        return Boolean.toString(value);
    }
}
