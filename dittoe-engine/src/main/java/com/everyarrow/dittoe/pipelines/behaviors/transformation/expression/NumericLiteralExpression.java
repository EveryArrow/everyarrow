package com.everyarrow.dittoe.pipelines.behaviors.transformation.expression;

import com.everyarrow.dittoe.pipelines.behaviors.transformation.ExpressionContext;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.DealSchema;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.ExpressionType;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.DealSourceSpan;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Represents a numeric literal expression that can be either an integer or a double.
 */
public class NumericLiteralExpression implements Expression {
    private final Number value;
    private final DealSourceSpan sourceSpan;

    public NumericLiteralExpression(Number value) {
        this(value, DealSourceSpan.unknown());
    }

    public NumericLiteralExpression(Number value, DealSourceSpan sourceSpan) {
        this.value = value;
        this.sourceSpan = sourceSpan;
    }

    @Override
    public Object evaluate(ExpressionContext context) {
        return value;
    }

    @Override
    public ExpressionType inferType(DealSchema schema) {
        if (value instanceof Byte || value instanceof Short || value instanceof Integer) {
            return new ExpressionType(ExpressionType.Kind.INTEGER, false);
        }
        if (value instanceof Long || value instanceof BigInteger) {
            return new ExpressionType(ExpressionType.Kind.NUMBER, false);
        }
        if (value instanceof BigDecimal) {
            return new ExpressionType(ExpressionType.Kind.DECIMAL, false);
        }
        return new ExpressionType(ExpressionType.Kind.DOUBLE, false);
    }

    @Override
    public DealSourceSpan sourceSpan() {
        return sourceSpan;
    }
}
