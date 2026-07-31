package com.everyarrow.dittoe.pipelines.behaviors.transformation.expression;

import com.everyarrow.dittoe.pipelines.behaviors.transformation.ExpressionContext;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.DealSchema;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.DealTypeSystem;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.ExpressionType;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.DealSourceSpan;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Set;

/** A prefix DEAL operator expression. */
public final class UnaryExpression implements Expression {
    public enum Operator {
        PLUS,
        NEGATE,
        NOT
    }

    private final Operator operator;
    private final Expression operand;
    private final DealSourceSpan sourceSpan;

    public UnaryExpression(Operator operator, Expression operand) {
        this(operator, operand, DealSourceSpan.unknown());
    }

    public UnaryExpression(Operator operator, Expression operand, DealSourceSpan sourceSpan) {
        this.operator = operator;
        this.operand = operand;
        this.sourceSpan = sourceSpan;
    }

    @Override
    public Object evaluate(ExpressionContext context) {
        Object value = operand.evaluate(context);
        return switch (operator) {
            case PLUS -> requireNumber(value);
            case NEGATE -> negate(requireNumber(value));
            case NOT -> !requireBoolean(value);
        };
    }

    @Override
    public void validate() {
        operand.validate();
    }

    @Override
    public Set<String> referencedFields() {
        return operand.referencedFields();
    }

    @Override
    public ExpressionType inferType(DealSchema schema) {
        return inferredAtSource(() -> {
            ExpressionType operandType = operand.inferType(schema);
            return switch (operator) {
                case PLUS, NEGATE -> DealTypeSystem.requireNumeric(operandType, "Unary numeric operator")
                        .withNullable(false);
                case NOT -> {
                    DealTypeSystem.requireBoolean(operandType, "Logical not");
                    yield new ExpressionType(ExpressionType.Kind.BOOLEAN, false);
                }
            };
        });
    }

    @Override
    public DealSourceSpan sourceSpan() {
        return sourceSpan;
    }

    private static Number requireNumber(Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Unary numeric operator requires a number");
        }
        return number;
    }

    private static boolean requireBoolean(Object value) {
        if (!(value instanceof Boolean booleanValue)) {
            throw new IllegalArgumentException("Logical not requires a boolean");
        }
        return booleanValue;
    }

    private static Number negate(Number number) {
        if (number instanceof Integer integer) {
            return Math.negateExact(integer);
        }
        if (number instanceof Long longValue) {
            return Math.negateExact(longValue);
        }
        if (number instanceof BigInteger bigInteger) {
            return bigInteger.negate();
        }
        if (number instanceof BigDecimal bigDecimal) {
            return bigDecimal.negate();
        }
        double result = -number.doubleValue();
        if (!Double.isFinite(result)) {
            throw new ArithmeticException("Unary numeric result is not finite");
        }
        return result;
    }
}
