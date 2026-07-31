package com.everyarrow.dittoe.pipelines.behaviors.transformation.expression;

import com.everyarrow.dittoe.pipelines.behaviors.transformation.FunctionRegistry;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.ExpressionContext;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.DealSchema;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.ExpressionType;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.DealSourceSpan;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

public class FunctionCallExpression implements Expression {
    private final String functionName;
    private final List<Expression> arguments;
    private final DealSourceSpan sourceSpan;

    public FunctionCallExpression(String functionName, List<Expression> arguments) {
        this(functionName, arguments, DealSourceSpan.unknown());
    }

    public FunctionCallExpression(String functionName,
                                  List<Expression> arguments,
                                  DealSourceSpan sourceSpan) {
        this.functionName = functionName;
        this.arguments = arguments;
        this.sourceSpan = sourceSpan;
    }

    @Override
    public Object evaluate(ExpressionContext context) {
        return FunctionRegistry.invoke(functionName, arguments, context);
    }

    @Override
    public void validate() {
        inferredAtSource(() -> {
            FunctionRegistry.validateInvocation(functionName, arguments.size());
            for (Expression argument : arguments) {
                argument.validate();
            }
            return ExpressionType.any();
        });
    }

    @Override
    public Set<String> referencedFields() {
        Set<String> references = new LinkedHashSet<>();
        for (Expression argument : arguments) {
            references.addAll(argument.referencedFields());
        }
        return Set.copyOf(references);
    }

    @Override
    public ExpressionType inferType(DealSchema schema) {
        return inferredAtSource(() -> FunctionRegistry.inferInvocation(functionName, arguments.stream()
                .map(argument -> argument.inferType(schema))
                .toList()));
    }

    @Override
    public DealSourceSpan sourceSpan() {
        return sourceSpan;
    }
}
