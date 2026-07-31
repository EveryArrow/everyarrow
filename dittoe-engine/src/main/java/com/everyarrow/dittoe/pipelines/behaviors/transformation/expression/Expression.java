package com.everyarrow.dittoe.pipelines.behaviors.transformation.expression;

import com.everyarrow.dittoe.pipelines.behaviors.transformation.ExpressionContext;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.DealSchema;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.ExpressionType;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.DealCompileFailure;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.DealSourceSpan;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.Parser;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.Token;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.Tokenizer;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public interface Expression {
    static Expression parse(String expression) {
        // Tokenize the input expression.
        Tokenizer tokenizer = new Tokenizer(expression);
        List<Token> tokens = tokenizer.tokenize();

        // Parse the tokens into an expression.
        Parser parser = new Parser(tokens);
        return parser.parse();
    }

    static Object evaluate(String expression, ExpressionContext context) {
        return parse(expression).evaluate(context);
    }

    Object evaluate(ExpressionContext context);

    /** Validates expression structure that does not require row values. */
    default void validate() {
    }

    default Set<String> referencedFields() {
        return Set.of();
    }

    /** Infers the expression's value type from the PMD Resource schema. */
    default ExpressionType inferType(DealSchema schema) {
        return ExpressionType.any();
    }

    default DealSourceSpan sourceSpan() {
        return DealSourceSpan.unknown();
    }

    default ExpressionType inferredAtSource(Supplier<ExpressionType> inference) {
        try {
            return inference.get();
        } catch (DealCompileFailure failure) {
            throw failure.at(sourceSpan());
        }
    }
}
