package com.everyarrow.dittoe.pipelines.behaviors.transformation.expression;

import com.everyarrow.dittoe.pipelines.behaviors.transformation.ExpressionContext;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.DealSchema;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.ExpressionType;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.DealSourceSpan;

public class StringLiteralExpression implements Expression {
    private final String value;
    private final DealSourceSpan sourceSpan;

    public StringLiteralExpression(String value) {
        this(value, DealSourceSpan.unknown());
    }

    public StringLiteralExpression(String value, DealSourceSpan sourceSpan) {
        this.value = value;
        this.sourceSpan = sourceSpan;
    }

    @Override
    public Object evaluate(ExpressionContext context) {
        return value;
    }

    @Override
    public ExpressionType inferType(DealSchema schema) {
        return new ExpressionType(ExpressionType.Kind.STRING, false);
    }

    @Override
    public DealSourceSpan sourceSpan() {
        return sourceSpan;
    }
}
