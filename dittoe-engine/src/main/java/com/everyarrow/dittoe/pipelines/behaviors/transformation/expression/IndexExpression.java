package com.everyarrow.dittoe.pipelines.behaviors.transformation.expression;

import com.everyarrow.dittoe.pipelines.behaviors.transformation.Assignable;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.ExpressionContext;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.DealSchema;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.DealTypeSystem;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.ExpressionType;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.DealSourceSpan;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

public class IndexExpression implements Expression, Assignable {
    private final Expression baseExpression;
    private final Expression indexExpression;
    private final DealSourceSpan sourceSpan;

    public IndexExpression(Expression baseExpression, Expression indexExpression) {
        this(baseExpression, indexExpression, DealSourceSpan.unknown());
    }

    public IndexExpression(Expression baseExpression,
                           Expression indexExpression,
                           DealSourceSpan sourceSpan) {
        this.baseExpression = baseExpression;
        this.indexExpression = indexExpression;
        this.sourceSpan = sourceSpan;
    }

    @Override
    public Object evaluate(ExpressionContext context) {
        Object base = baseExpression.evaluate(context);
        Object indexObj = indexExpression.evaluate(context);
        if (!(indexObj instanceof Number)) {
            throw new RuntimeException("Index is not a number: " + indexObj);
        }
        int index = ((Number) indexObj).intValue();

        if (base instanceof List<?> list) {
            if (index < 0 || index >= list.size()) {
                throw new RuntimeException("Index out of bounds: " + index);
            }
            return list.get(index);
        } else if (base != null && base.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(base);
            if (index < 0 || index >= length) {
                throw new RuntimeException("Index out of bounds: " + index);
            }
            return java.lang.reflect.Array.get(base, index);
        } else {
            throw new RuntimeException("Object is not indexable: " + base);
        }
    }

    @Override
    public void validate() {
        baseExpression.validate();
        indexExpression.validate();
    }

    @Override
    public void assign(ExpressionContext context, Object value) {
        Object base = baseExpression.evaluate(context);
        Object indexObj = indexExpression.evaluate(context);
        if (!(indexObj instanceof Number)) {
            throw new RuntimeException("Index is not a number: " + indexObj);
        }
        int index = ((Number) indexObj).intValue();

        if (base instanceof List<?> list) {
            if (index < 0 || index >= list.size()) {
                throw new RuntimeException("Index out of bounds: " + index);
            }
            assignListValue(list, index, value);
        } else if (base != null && base.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(base);
            if (index < 0 || index >= length) {
                throw new RuntimeException("Index out of bounds: " + index);
            }
            java.lang.reflect.Array.set(base, index, value);
        } else {
            throw new RuntimeException("Object is not indexable: " + base);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void assignListValue(List<?> list, int index, Object value) {
        ((List) list).set(index, value);
    }

    @Override
    public Set<String> referencedFields() {
        return indexReferences();
    }

    @Override
    public Set<String> assignmentReferences() {
        return indexReferences();
    }

    private Set<String> indexReferences() {
        Set<String> references = new LinkedHashSet<>(baseExpression.referencedFields());
        references.addAll(indexExpression.referencedFields());
        return Set.copyOf(references);
    }

    @Override
    public ExpressionType inferType(DealSchema schema) {
        return inferredAtSource(() -> {
            DealTypeSystem.requireIndexable(baseExpression.inferType(schema));
            DealTypeSystem.requireNumeric(indexExpression.inferType(schema), "Array index");
            return ExpressionType.any();
        });
    }

    @Override
    public DealSourceSpan sourceSpan() {
        return sourceSpan;
    }
}
