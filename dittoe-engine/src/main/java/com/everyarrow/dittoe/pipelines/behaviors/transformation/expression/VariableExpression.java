package com.everyarrow.dittoe.pipelines.behaviors.transformation.expression;

import com.everyarrow.dittoe.pipelines.behaviors.transformation.Assignable;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.ExpressionContext;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.DealSchema;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.ExpressionType;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.DealSourceSpan;

import java.util.Set;

public class VariableExpression implements Expression, Assignable {
    private final String variableName;
    private final DealSourceSpan sourceSpan;

    public VariableExpression(String variableName) {
        this(variableName, DealSourceSpan.unknown());
    }

    public VariableExpression(String variableName, DealSourceSpan sourceSpan) {
        this.variableName = variableName;
        this.sourceSpan = sourceSpan;
    }

    @Override
    public Object evaluate(ExpressionContext context) {
        return context.requireVariable(variableName);
    }

    public String getVariableName() {
        return variableName;
    }

    @Override
    public void assign(ExpressionContext context, Object value) {
        context.setVariable(variableName, value);
    }

    @Override
    public Set<String> referencedFields() {
        return Set.of(variableName);
    }

    @Override
    public ExpressionType inferType(DealSchema schema) {
        DealSchema.FieldDefinition field = schema.requireField(variableName);
        return ExpressionType.of(field.dataType(), field.nullable());
    }

    @Override
    public DealSourceSpan sourceSpan() {
        return sourceSpan;
    }
}
