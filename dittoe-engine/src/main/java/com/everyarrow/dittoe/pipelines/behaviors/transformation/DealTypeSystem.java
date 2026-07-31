package com.everyarrow.dittoe.pipelines.behaviors.transformation;

import java.util.Objects;

/** Compile-time type, nullability, and coercion rules for DEAL. */
public final class DealTypeSystem {
    private DealTypeSystem() {
    }

    public static ExpressionType requireNumeric(ExpressionType type, String operation) {
        Objects.requireNonNull(type, "type");
        if (type.kind() == ExpressionType.Kind.ANY) {
            return type;
        }
        requirePresent(type, operation);
        if (!type.isNumeric()) {
            throw typeMismatch(operation + " requires NUMBER but received " + type);
        }
        return type;
    }

    public static ExpressionType requireBoolean(ExpressionType type, String operation) {
        Objects.requireNonNull(type, "type");
        if (type.kind() == ExpressionType.Kind.ANY) {
            return type;
        }
        requirePresent(type, operation);
        if (type.kind() != ExpressionType.Kind.BOOLEAN) {
            throw typeMismatch(operation + " requires BOOLEAN but received " + type);
        }
        return type;
    }

    public static ExpressionType arithmetic(ExpressionType left,
                                            ExpressionType right,
                                            boolean division) {
        requireNumeric(left, "Arithmetic operator");
        requireNumeric(right, "Arithmetic operator");
        if (left.kind() == ExpressionType.Kind.ANY || right.kind() == ExpressionType.Kind.ANY) {
            return new ExpressionType(ExpressionType.Kind.NUMBER, false);
        }
        if (division && left.kind() != ExpressionType.Kind.DECIMAL
                && right.kind() != ExpressionType.Kind.DECIMAL) {
            return new ExpressionType(ExpressionType.Kind.DOUBLE, false);
        }
        if (left.kind() == ExpressionType.Kind.NUMBER || right.kind() == ExpressionType.Kind.NUMBER) {
            return new ExpressionType(ExpressionType.Kind.NUMBER, false);
        }
        if (left.kind() == ExpressionType.Kind.DECIMAL || right.kind() == ExpressionType.Kind.DECIMAL) {
            return new ExpressionType(ExpressionType.Kind.DECIMAL, false);
        }
        if (left.kind() == ExpressionType.Kind.DOUBLE || right.kind() == ExpressionType.Kind.DOUBLE) {
            return new ExpressionType(ExpressionType.Kind.DOUBLE, false);
        }
        return new ExpressionType(ExpressionType.Kind.INTEGER, false);
    }

    public static ExpressionType commonType(ExpressionType left,
                                            ExpressionType right,
                                            String operation) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        if (left.kind() == ExpressionType.Kind.ANY || right.kind() == ExpressionType.Kind.ANY) {
            return ExpressionType.any();
        }
        if (left.kind() == ExpressionType.Kind.NULL && right.kind() == ExpressionType.Kind.NULL) {
            return ExpressionType.nullValue();
        }
        if (left.kind() == ExpressionType.Kind.NULL) {
            return right.withNullable(true);
        }
        if (right.kind() == ExpressionType.Kind.NULL) {
            return left.withNullable(true);
        }
        if (left.kind() == right.kind()) {
            return new ExpressionType(left.kind(), left.nullable() || right.nullable());
        }
        if (left.isNumeric() && right.isNumeric()) {
            return commonNumericType(left, right)
                    .withNullable(left.nullable() || right.nullable());
        }
        throw typeMismatch(
                operation + " has incompatible types " + left + " and " + right);
    }

    public static ExpressionType coalesce(ExpressionType left, ExpressionType right) {
        ExpressionType common = commonType(left, right, "Null coalescing");
        if (common.kind() == ExpressionType.Kind.NULL) {
            return common;
        }
        return common.withNullable(left.nullable() && right.nullable());
    }

    public static void requireComparable(ExpressionType left, ExpressionType right) {
        if (left.kind() == ExpressionType.Kind.ANY || right.kind() == ExpressionType.Kind.ANY) {
            return;
        }
        requirePresent(left, "Ordering comparison");
        requirePresent(right, "Ordering comparison");
        if (left.isNumeric() && right.isNumeric()) {
            return;
        }
        if (left.kind() != right.kind() || !isOrderable(left.kind())) {
            throw typeMismatch(
                    "Ordering comparison has incompatible types " + left + " and " + right);
        }
    }

    public static void requireIndexable(ExpressionType type) {
        if (type.kind() == ExpressionType.Kind.ANY) {
            return;
        }
        requirePresent(type, "Index operator");
        if (type.kind() != ExpressionType.Kind.ARRAY && type.kind() != ExpressionType.Kind.BYTES) {
            throw typeMismatch("Index operator requires ARRAY or BYTES but received " + type);
        }
    }

    public static void requireAssignable(ExpressionType actual,
                                         DealSchema.FieldDefinition target,
                                         String context) {
        Objects.requireNonNull(actual, "actual");
        Objects.requireNonNull(target, "target");
        if (actual.kind() == ExpressionType.Kind.ANY) {
            return;
        }
        if (actual.kind() == ExpressionType.Kind.NULL) {
            if (target.nullable()) {
                return;
            }
            throw nullabilityMismatch(context + " is NULL but PMD Resource field '"
                    + target.name() + "' is non-nullable");
        }
        if (actual.nullable() && !target.nullable()) {
            throw nullabilityMismatch(context + " has nullable type " + actual.kind()
                    + " but PMD Resource field '" + target.name() + "' is non-nullable");
        }

        ExpressionType.Kind targetKind = ExpressionType.Kind.valueOf(target.dataType().name());
        if (actual.kind() == targetKind || isJsonCompatible(actual.kind(), targetKind)
                || isSerializedRepresentation(actual.kind(), targetKind)
                || isSafeNumericCoercion(actual.kind(), targetKind)) {
            return;
        }
        throw typeMismatch(context + " has type " + actual.kind()
                + " but PMD Resource field '" + target.name() + "' declares " + target.dataType()
                + conversionHint(target.dataType()));
    }

    private static void requirePresent(ExpressionType type, String operation) {
        if (type.kind() == ExpressionType.Kind.NULL || type.nullable()) {
            throw nullabilityMismatch(operation + " does not accept " + type);
        }
    }

    private static ExpressionType commonNumericType(ExpressionType left, ExpressionType right) {
        ExpressionType.Kind kind;
        if (left.kind() == ExpressionType.Kind.NUMBER || right.kind() == ExpressionType.Kind.NUMBER
                || (left.kind() == ExpressionType.Kind.DOUBLE && right.kind() == ExpressionType.Kind.DECIMAL)
                || (left.kind() == ExpressionType.Kind.DECIMAL && right.kind() == ExpressionType.Kind.DOUBLE)) {
            kind = ExpressionType.Kind.NUMBER;
        } else if (left.kind() == ExpressionType.Kind.DECIMAL || right.kind() == ExpressionType.Kind.DECIMAL) {
            kind = ExpressionType.Kind.DECIMAL;
        } else if (left.kind() == ExpressionType.Kind.DOUBLE || right.kind() == ExpressionType.Kind.DOUBLE) {
            kind = ExpressionType.Kind.DOUBLE;
        } else {
            kind = ExpressionType.Kind.INTEGER;
        }
        return new ExpressionType(kind, left.nullable() || right.nullable());
    }

    private static boolean isOrderable(ExpressionType.Kind kind) {
        return kind == ExpressionType.Kind.STRING
                || kind == ExpressionType.Kind.DATE
                || kind == ExpressionType.Kind.DATE_TIME
                || kind == ExpressionType.Kind.TIME
                || kind == ExpressionType.Kind.UUID
                || kind == ExpressionType.Kind.TIME_WITH_TIME_ZONE
                || kind == ExpressionType.Kind.DATE_TIME_WITH_TIME_ZONE;
    }

    private static boolean isJsonCompatible(ExpressionType.Kind actual, ExpressionType.Kind target) {
        return (actual == ExpressionType.Kind.JSON || actual == ExpressionType.Kind.JSONB)
                && (target == ExpressionType.Kind.JSON || target == ExpressionType.Kind.JSONB);
    }

    private static boolean isSerializedRepresentation(ExpressionType.Kind actual, ExpressionType.Kind target) {
        boolean jsonRepresentation = (actual == ExpressionType.Kind.STRING || actual == ExpressionType.Kind.ARRAY)
                && (target == ExpressionType.Kind.JSON || target == ExpressionType.Kind.JSONB);
        return jsonRepresentation
                || actual == ExpressionType.Kind.STRING && target == ExpressionType.Kind.XML;
    }

    private static boolean isSafeNumericCoercion(ExpressionType.Kind actual, ExpressionType.Kind target) {
        return switch (actual) {
            case INTEGER -> target == ExpressionType.Kind.DOUBLE
                    || target == ExpressionType.Kind.DECIMAL
                    || target == ExpressionType.Kind.NUMBER;
            case DOUBLE, DECIMAL -> target == ExpressionType.Kind.NUMBER;
            default -> false;
        };
    }

    private static String conversionHint(DealType targetType) {
        return switch (targetType) {
            case STRING -> "; use to_string(...) for an explicit conversion";
            case INTEGER -> "; use to_integer(...) for an explicit conversion";
            case DOUBLE -> "; use to_double(...) for an explicit conversion";
            case DECIMAL -> "; use to_decimal(...) for an explicit conversion";
            case BOOLEAN -> "; use to_boolean(...) for an explicit conversion";
            case DATE -> "; use to_date(...) for an explicit conversion";
            case DATE_TIME -> "; use to_datetime(...) for an explicit conversion";
            case TIME -> "; use to_time(...) for an explicit conversion";
            case UUID -> "; use to_uuid(...) for an explicit conversion";
            default -> "";
        };
    }

    private static DealCompileFailure typeMismatch(String message) {
        return new DealCompileFailure(DealDiagnosticCode.TYPE_MISMATCH, message);
    }

    private static DealCompileFailure nullabilityMismatch(String message) {
        return new DealCompileFailure(DealDiagnosticCode.NULLABILITY_MISMATCH, message);
    }
}
