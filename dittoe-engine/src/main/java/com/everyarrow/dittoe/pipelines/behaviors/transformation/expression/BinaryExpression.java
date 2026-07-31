package com.everyarrow.dittoe.pipelines.behaviors.transformation.expression;

import com.everyarrow.dittoe.pipelines.behaviors.transformation.ExpressionContext;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.DealSchema;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.DealTypeSystem;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.ExpressionType;
import com.everyarrow.dittoe.pipelines.behaviors.transformation.DealSourceSpan;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** A binary DEAL operator expression with standard short-circuit semantics. */
public final class BinaryExpression implements Expression {
    public enum Operator {
        ADD,
        SUBTRACT,
        MULTIPLY,
        DIVIDE,
        REMAINDER,
        EQUAL,
        NOT_EQUAL,
        LESS,
        LESS_EQUAL,
        GREATER,
        GREATER_EQUAL,
        AND,
        OR,
        COALESCE
    }

    private final Expression left;
    private final Operator operator;
    private final Expression right;
    private final DealSourceSpan sourceSpan;

    public BinaryExpression(Expression left, Operator operator, Expression right) {
        this(left, operator, right, DealSourceSpan.unknown());
    }

    public BinaryExpression(Expression left,
                            Operator operator,
                            Expression right,
                            DealSourceSpan sourceSpan) {
        this.left = left;
        this.operator = operator;
        this.right = right;
        this.sourceSpan = sourceSpan;
    }

    @Override
    public Object evaluate(ExpressionContext context) {
        Object leftValue = left.evaluate(context);
        return switch (operator) {
            case AND -> requireBoolean(leftValue, "Left operand of &&")
                    ? requireBoolean(right.evaluate(context), "Right operand of &&")
                    : false;
            case OR -> requireBoolean(leftValue, "Left operand of ||")
                    ? true
                    : requireBoolean(right.evaluate(context), "Right operand of ||");
            case COALESCE -> leftValue != null ? leftValue : right.evaluate(context);
            default -> evaluateEager(leftValue, right.evaluate(context));
        };
    }

    @Override
    public void validate() {
        left.validate();
        right.validate();
    }

    @Override
    public Set<String> referencedFields() {
        Set<String> references = new LinkedHashSet<>(left.referencedFields());
        references.addAll(right.referencedFields());
        return Set.copyOf(references);
    }

    @Override
    public ExpressionType inferType(DealSchema schema) {
        return inferredAtSource(() -> {
            ExpressionType leftType = left.inferType(schema);
            ExpressionType rightType = right.inferType(schema);
            return switch (operator) {
                case ADD, SUBTRACT, MULTIPLY, REMAINDER ->
                        DealTypeSystem.arithmetic(leftType, rightType, false);
                case DIVIDE -> DealTypeSystem.arithmetic(leftType, rightType, true);
                case EQUAL, NOT_EQUAL -> new ExpressionType(ExpressionType.Kind.BOOLEAN, false);
                case LESS, LESS_EQUAL, GREATER, GREATER_EQUAL -> {
                    DealTypeSystem.requireComparable(leftType, rightType);
                    yield new ExpressionType(ExpressionType.Kind.BOOLEAN, false);
                }
                case AND, OR -> {
                    DealTypeSystem.requireBoolean(leftType, "Logical operator");
                    DealTypeSystem.requireBoolean(rightType, "Logical operator");
                    yield new ExpressionType(ExpressionType.Kind.BOOLEAN, false);
                }
                case COALESCE -> DealTypeSystem.coalesce(leftType, rightType);
            };
        });
    }

    @Override
    public DealSourceSpan sourceSpan() {
        return sourceSpan;
    }

    private Object evaluateEager(Object leftValue, Object rightValue) {
        return switch (operator) {
            case ADD -> arithmetic(leftValue, rightValue, Arithmetic.ADD);
            case SUBTRACT -> arithmetic(leftValue, rightValue, Arithmetic.SUBTRACT);
            case MULTIPLY -> arithmetic(leftValue, rightValue, Arithmetic.MULTIPLY);
            case DIVIDE -> arithmetic(leftValue, rightValue, Arithmetic.DIVIDE);
            case REMAINDER -> arithmetic(leftValue, rightValue, Arithmetic.REMAINDER);
            case EQUAL -> equalValues(leftValue, rightValue);
            case NOT_EQUAL -> !equalValues(leftValue, rightValue);
            case LESS -> compare(leftValue, rightValue) < 0;
            case LESS_EQUAL -> compare(leftValue, rightValue) <= 0;
            case GREATER -> compare(leftValue, rightValue) > 0;
            case GREATER_EQUAL -> compare(leftValue, rightValue) >= 0;
            case AND, OR, COALESCE -> throw new IllegalStateException("Lazy operator evaluated eagerly");
        };
    }

    private static Object arithmetic(Object leftValue, Object rightValue, Arithmetic operation) {
        if (!(leftValue instanceof Number leftNumber) || !(rightValue instanceof Number rightNumber)) {
            throw new IllegalArgumentException("Arithmetic operators require numeric operands");
        }
        if (leftNumber instanceof Integer leftInteger && rightNumber instanceof Integer rightInteger) {
            return switch (operation) {
                case ADD -> Math.addExact(leftInteger, rightInteger);
                case SUBTRACT -> Math.subtractExact(leftInteger, rightInteger);
                case MULTIPLY -> Math.multiplyExact(leftInteger, rightInteger);
                case DIVIDE -> divide(leftInteger.doubleValue(), rightInteger.doubleValue());
                case REMAINDER -> {
                    if (rightInteger == 0) {
                        throw new ArithmeticException("Division by zero");
                    }
                    yield leftInteger % rightInteger;
                }
            };
        }
        if (usesDecimalArithmetic(leftNumber, rightNumber)) {
            BigDecimal leftDecimal = decimal(leftNumber);
            BigDecimal rightDecimal = decimal(rightNumber);
            return switch (operation) {
                case ADD -> leftDecimal.add(rightDecimal);
                case SUBTRACT -> leftDecimal.subtract(rightDecimal);
                case MULTIPLY -> leftDecimal.multiply(rightDecimal);
                case DIVIDE -> leftDecimal.divide(rightDecimal, MathContext.DECIMAL128);
                case REMAINDER -> leftDecimal.remainder(rightDecimal);
            };
        }

        double leftDouble = finite(leftNumber.doubleValue());
        double rightDouble = finite(rightNumber.doubleValue());
        double result = switch (operation) {
            case ADD -> leftDouble + rightDouble;
            case SUBTRACT -> leftDouble - rightDouble;
            case MULTIPLY -> leftDouble * rightDouble;
            case DIVIDE -> divide(leftDouble, rightDouble);
            case REMAINDER -> {
                if (rightDouble == 0.0d) {
                    throw new ArithmeticException("Division by zero");
                }
                yield leftDouble % rightDouble;
            }
        };
        return finite(result);
    }

    private static double divide(double left, double right) {
        if (right == 0.0d) {
            throw new ArithmeticException("Division by zero");
        }
        return finite(left / right);
    }

    private static boolean equalValues(Object leftValue, Object rightValue) {
        if (leftValue instanceof Number leftNumber && rightValue instanceof Number rightNumber) {
            return compareNumbers(leftNumber, rightNumber) == 0;
        }
        return Objects.equals(leftValue, rightValue);
    }

    private static int compare(Object leftValue, Object rightValue) {
        if (leftValue == null || rightValue == null) {
            throw new IllegalArgumentException("Ordering comparisons do not accept null operands");
        }
        if (leftValue instanceof Number leftNumber && rightValue instanceof Number rightNumber) {
            return compareNumbers(leftNumber, rightNumber);
        }
        if (leftValue.getClass().equals(rightValue.getClass()) && leftValue instanceof Comparable<?> comparable) {
            return compareSameType(comparable, rightValue);
        }
        throw new IllegalArgumentException(
                "Values are not order-comparable: " + leftValue.getClass().getSimpleName()
                        + " and " + rightValue.getClass().getSimpleName());
    }

    @SuppressWarnings("unchecked")
    private static int compareSameType(Comparable<?> leftValue, Object rightValue) {
        return ((Comparable<Object>) leftValue).compareTo(rightValue);
    }

    private static int compareNumbers(Number left, Number right) {
        if (usesDecimalArithmetic(left, right)) {
            return decimal(left).compareTo(decimal(right));
        }
        return Double.compare(finite(left.doubleValue()), finite(right.doubleValue()));
    }

    private static boolean usesDecimalArithmetic(Number left, Number right) {
        return left instanceof BigDecimal || left instanceof BigInteger
                || right instanceof BigDecimal || right instanceof BigInteger;
    }

    private static BigDecimal decimal(Number number) {
        if (number instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (number instanceof BigInteger bigInteger) {
            return new BigDecimal(bigInteger);
        }
        if (number instanceof Byte || number instanceof Short || number instanceof Integer || number instanceof Long) {
            return BigDecimal.valueOf(number.longValue());
        }
        return BigDecimal.valueOf(finite(number.doubleValue()));
    }

    private static boolean requireBoolean(Object value, String operand) {
        if (!(value instanceof Boolean booleanValue)) {
            throw new IllegalArgumentException(operand + " must be boolean");
        }
        return booleanValue;
    }

    private static double finite(double value) {
        if (!Double.isFinite(value)) {
            throw new ArithmeticException("Numeric result is not finite");
        }
        return value;
    }

    private enum Arithmetic {
        ADD,
        SUBTRACT,
        MULTIPLY,
        DIVIDE,
        REMAINDER
    }
}
