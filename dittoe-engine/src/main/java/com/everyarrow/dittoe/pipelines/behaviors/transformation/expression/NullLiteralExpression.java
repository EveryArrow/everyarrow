package com.everyarrow.dittoe.pipelines.behaviors.transformation.expression;

import com.everyarrow.dittoe.pipelines.behaviors.transformation.ExpressionContext;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.DealSchema;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.ExpressionType;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.DealSourceSpan;

/** The DEAL null literal. */
public final class NullLiteralExpression implements Expression {
    private final DealSourceSpan sourceSpan;

    public NullLiteralExpression() {
        this(DealSourceSpan.unknown());
    }

    public NullLiteralExpression(DealSourceSpan sourceSpan) {
        this.sourceSpan = sourceSpan;
    }

    @Override
    public Object evaluate(ExpressionContext context) {
        return null;
    }

    @Override
    public ExpressionType inferType(DealSchema schema) {
        return ExpressionType.nullValue();
    }

    @Override
    public DealSourceSpan sourceSpan() {
        return sourceSpan;
    }
}
