package com.everyarrow.dittoe.pipelines.behaviors.transformation.expression;

import com.everyarrow.dittoe.pipelines.behaviors.transformation.Assignable;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.ExpressionContext;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.DealSchema;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.DealTypeSystem;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.ExpressionType;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.DealSourceSpan;

import java.util.LinkedHashSet;
import java.util.Set;

public class AssignmentExpression implements Expression {
    private final Assignable lhs;
    private final Expression rhs;
    private final DealSourceSpan sourceSpan;

    public AssignmentExpression(Assignable lhs, Expression rhs) {
        this(lhs, rhs, DealSourceSpan.unknown());
    }

    public AssignmentExpression(Assignable lhs, Expression rhs, DealSourceSpan sourceSpan) {
        this.lhs = lhs;
        this.rhs = rhs;
        this.sourceSpan = sourceSpan;
    }

    @Override
    public Object evaluate(ExpressionContext context) {
        Object value = rhs.evaluate(context);
        lhs.assign(context, value);
        return value;
    }

    @Override
    public void validate() {
        if (lhs instanceof Expression expression) {
            expression.validate();
        }
        rhs.validate();
    }

    @Override
    public Set<String> referencedFields() {
        Set<String> references = new LinkedHashSet<>(lhs.assignmentReferences());
        references.addAll(rhs.referencedFields());
        return Set.copyOf(references);
    }

    @Override
    public ExpressionType inferType(DealSchema schema) {
        return inferredAtSource(() -> {
            ExpressionType valueType = rhs.inferType(schema);
            if (lhs instanceof VariableExpression variable) {
                DealSchema.FieldDefinition target = schema.requireField(variable.getVariableName());
                DealTypeSystem.requireAssignable(valueType, target,
                        "DEAL assignment to '" + target.name() + "'");
            } else if (lhs instanceof Expression expression) {
                expression.inferType(schema);
            }
            return valueType;
        });
    }

    @Override
    public DealSourceSpan sourceSpan() {
        return sourceSpan;
    }
}
