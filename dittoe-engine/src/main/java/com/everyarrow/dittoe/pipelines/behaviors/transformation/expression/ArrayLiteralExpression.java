package com.everyarrow.dittoe.pipelines.behaviors.transformation.expression;

import com.everyarrow.dittoe.pipelines.behaviors.transformation.ExpressionContext;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.DealSchema;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.ExpressionType;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.DealSourceSpan;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents an array literal (e.g. [1, 2, 3]).
 */
public class ArrayLiteralExpression implements Expression {

    private final List<Expression> elements;
    private final DealSourceSpan sourceSpan;

    public ArrayLiteralExpression(List<Expression> elements) {
        this(elements, DealSourceSpan.unknown());
    }

    public ArrayLiteralExpression(List<Expression> elements, DealSourceSpan sourceSpan) {
        this.elements = elements;
        this.sourceSpan = sourceSpan;
    }

    @Override
    public Object evaluate(ExpressionContext context) {
        List<Object> evaluated = new ArrayList<>();
        for (Expression e : elements) {
            evaluated.add(e.evaluate(context));
        }
        return evaluated;
    }

    @Override
    public void validate() {
        for (Expression element : elements) {
            element.validate();
        }
    }

    @Override
    public Set<String> referencedFields() {
        Set<String> references = new LinkedHashSet<>();
        for (Expression element : elements) {
            references.addAll(element.referencedFields());
        }
        return Set.copyOf(references);
    }

    @Override
    public ExpressionType inferType(DealSchema schema) {
        for (Expression element : elements) {
            element.inferType(schema);
        }
        return new ExpressionType(ExpressionType.Kind.ARRAY, false);
    }

    @Override
    public DealSourceSpan sourceSpan() {
        return sourceSpan;
    }
}
