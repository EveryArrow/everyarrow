package com.everyarrow.dittoe.pipelines.behaviors.transformation;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Resolves DEAL function signatures against compile-time expression types. */
final class FunctionTypeChecker {
    private FunctionTypeChecker() {
    }

    static ExpressionType infer(FunctionDescriptor descriptor, List<ExpressionType> argumentTypes) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(argumentTypes, "argumentTypes");
        FunctionSignature signature = descriptor.requireSignature(argumentTypes.size());
        List<FunctionValueType> expectedTypes = signature.argumentTypes(argumentTypes.size()).orElseThrow();
        for (int i = 0; i < argumentTypes.size(); i++) {
            ExpressionType actual = argumentTypes.get(i);
            FunctionValueType expected = expectedTypes.get(i);
            if (expected != FunctionValueType.ANY
                    && actual.kind() != ExpressionType.Kind.ANY
                    && (actual.kind() == ExpressionType.Kind.NULL || actual.nullable())) {
                throw new DealCompileFailure(DealDiagnosticCode.NULLABILITY_MISMATCH,
                        "DEAL function '" + descriptor.name() + "' argument " + (i + 1)
                                + " expected " + expected + " but expression has type " + actual);
            }
            if (!accepts(expected, actual)) {
                throw new DealCompileFailure(DealDiagnosticCode.TYPE_MISMATCH,
                        "DEAL function '" + descriptor.name() + "' argument " + (i + 1)
                                + " expected " + expected + " but expression has type " + actual);
            }
        }

        validateConversionSource(descriptor.name(), argumentTypes);
        return inferResultType(descriptor.name(), signature.resultType(), argumentTypes);
    }

    private static boolean accepts(FunctionValueType expected, ExpressionType actual) {
        if (expected == FunctionValueType.ANY || actual.kind() == ExpressionType.Kind.ANY) {
            return true;
        }
        if (actual.kind() == ExpressionType.Kind.NULL || actual.nullable()) {
            return false;
        }
        return switch (expected) {
            case ANY -> true;
            case NUMBER -> actual.isNumeric();
            case DOUBLE -> actual.kind() == ExpressionType.Kind.DOUBLE;
            case DECIMAL -> actual.kind() == ExpressionType.Kind.DECIMAL;
            case INTEGER -> actual.kind() == ExpressionType.Kind.INTEGER;
            case STRING -> actual.kind() == ExpressionType.Kind.STRING;
            case BOOLEAN -> actual.kind() == ExpressionType.Kind.BOOLEAN;
            case DATE -> actual.kind() == ExpressionType.Kind.DATE;
            case DATE_TIME -> actual.kind() == ExpressionType.Kind.DATE_TIME;
            case TIME -> actual.kind() == ExpressionType.Kind.TIME;
            case DATE_OR_DATE_TIME -> actual.kind() == ExpressionType.Kind.DATE
                    || actual.kind() == ExpressionType.Kind.DATE_TIME;
            case TIME_OR_DATE_TIME -> actual.kind() == ExpressionType.Kind.TIME
                    || actual.kind() == ExpressionType.Kind.DATE_TIME;
            case TEMPORAL -> actual.kind() == ExpressionType.Kind.DATE
                    || actual.kind() == ExpressionType.Kind.DATE_TIME
                    || actual.kind() == ExpressionType.Kind.TIME;
            case UUID -> actual.kind() == ExpressionType.Kind.UUID;
        };
    }

    private static void validateConversionSource(String functionName, List<ExpressionType> argumentTypes) {
        if (argumentTypes.size() != 1) {
            return;
        }
        ExpressionType source = argumentTypes.getFirst();
        if (source.kind() == ExpressionType.Kind.ANY || source.kind() == ExpressionType.Kind.NULL) {
            return;
        }
        boolean supported = switch (functionName) {
            case "to_integer", "to_double", "to_decimal" ->
                    source.isNumeric() || source.kind() == ExpressionType.Kind.STRING;
            case "to_boolean" -> source.kind() == ExpressionType.Kind.BOOLEAN
                    || source.kind() == ExpressionType.Kind.STRING;
            case "to_date" -> source.kind() == ExpressionType.Kind.DATE
                    || source.kind() == ExpressionType.Kind.DATE_TIME
                    || source.kind() == ExpressionType.Kind.STRING;
            case "to_datetime" -> source.kind() == ExpressionType.Kind.DATE_TIME
                    || source.kind() == ExpressionType.Kind.DATE
                    || source.kind() == ExpressionType.Kind.STRING;
            case "to_time" -> source.kind() == ExpressionType.Kind.TIME
                    || source.kind() == ExpressionType.Kind.DATE_TIME
                    || source.kind() == ExpressionType.Kind.STRING;
            case "to_uuid" -> source.kind() == ExpressionType.Kind.UUID
                    || source.kind() == ExpressionType.Kind.STRING;
            default -> true;
        };
        if (!supported) {
            throw new DealCompileFailure(DealDiagnosticCode.UNSUPPORTED_CONVERSION,
                    "DEAL function '" + functionName + "' cannot convert expression type " + source);
        }
    }

    private static ExpressionType inferResultType(String functionName,
                                                  FunctionValueType declaredResult,
                                                  List<ExpressionType> argumentTypes) {
        return switch (functionName) {
            case "coalesce" -> inferCoalesceType(argumentTypes);
            case "nullif" -> argumentTypes.getFirst().withNullable(true);
            case "if", "caseWhen" -> inferConditionalType(functionName, argumentTypes);
            case "to_string" -> convertedType(ExpressionType.Kind.STRING, argumentTypes.getFirst());
            case "to_integer" -> convertedType(ExpressionType.Kind.INTEGER, argumentTypes.getFirst());
            case "to_double" -> convertedType(ExpressionType.Kind.DOUBLE, argumentTypes.getFirst());
            case "to_decimal" -> convertedType(ExpressionType.Kind.DECIMAL, argumentTypes.getFirst());
            case "to_boolean" -> convertedType(ExpressionType.Kind.BOOLEAN, argumentTypes.getFirst());
            case "to_date" -> convertedType(ExpressionType.Kind.DATE, argumentTypes.getFirst());
            case "to_datetime" -> convertedType(ExpressionType.Kind.DATE_TIME, argumentTypes.getFirst());
            case "to_time" -> convertedType(ExpressionType.Kind.TIME, argumentTypes.getFirst());
            case "to_uuid" -> convertedType(ExpressionType.Kind.UUID, argumentTypes.getFirst());
            case "is_null", "is_not_null" -> new ExpressionType(ExpressionType.Kind.BOOLEAN, false);
            default -> declaredResult == FunctionValueType.NUMBER
                    ? inferNumericResult(functionName, argumentTypes)
                    : fromFunctionType(declaredResult);
        };
    }

    private static ExpressionType inferCoalesceType(List<ExpressionType> argumentTypes) {
        if (argumentTypes.isEmpty()) {
            return ExpressionType.nullValue();
        }
        ExpressionType result = argumentTypes.getFirst();
        for (int i = 1; i < argumentTypes.size(); i++) {
            result = DealTypeSystem.coalesce(result, argumentTypes.get(i));
        }
        return result;
    }

    private static ExpressionType inferConditionalType(String functionName,
                                                       List<ExpressionType> argumentTypes) {
        ExpressionType result = argumentTypes.get(1);
        for (int i = 3; i < argumentTypes.size() - 1; i += 2) {
            result = DealTypeSystem.commonType(result, argumentTypes.get(i),
                    "DEAL function '" + functionName + "' result branches");
        }
        return DealTypeSystem.commonType(result, argumentTypes.getLast(),
                "DEAL function '" + functionName + "' result branches");
    }

    private static ExpressionType convertedType(ExpressionType.Kind kind, ExpressionType source) {
        return new ExpressionType(kind,
                source.nullable() || source.kind() == ExpressionType.Kind.NULL
                        || source.kind() == ExpressionType.Kind.ANY);
    }

    private static ExpressionType inferNumericResult(String functionName,
                                                     List<ExpressionType> argumentTypes) {
        boolean allIntegers = argumentTypes.stream()
                .allMatch(type -> type.kind() == ExpressionType.Kind.INTEGER);
        if (allIntegers && Set.of(
                "sum", "multiply", "abs", "ceiling", "floor", "mod", "round",
                "sign", "greatest", "least", "trunc").contains(functionName)) {
            return new ExpressionType(ExpressionType.Kind.INTEGER, false);
        }
        if (Set.of(
                "sum", "multiply", "abs", "acos", "asin", "atan", "atan2", "ceiling",
                "cos", "cot", "degrees", "exp", "floor", "log", "log10", "mod",
                "radians", "sign", "sin", "tan").contains(functionName)) {
            return new ExpressionType(ExpressionType.Kind.DOUBLE, false);
        }
        return new ExpressionType(ExpressionType.Kind.NUMBER, false);
    }

    private static ExpressionType fromFunctionType(FunctionValueType type) {
        return switch (type) {
            case ANY -> ExpressionType.any();
            case NUMBER -> new ExpressionType(ExpressionType.Kind.NUMBER, false);
            case DOUBLE -> new ExpressionType(ExpressionType.Kind.DOUBLE, false);
            case DECIMAL -> new ExpressionType(ExpressionType.Kind.DECIMAL, false);
            case INTEGER -> new ExpressionType(ExpressionType.Kind.INTEGER, false);
            case STRING -> new ExpressionType(ExpressionType.Kind.STRING, false);
            case BOOLEAN -> new ExpressionType(ExpressionType.Kind.BOOLEAN, false);
            case DATE -> new ExpressionType(ExpressionType.Kind.DATE, false);
            case DATE_TIME -> new ExpressionType(ExpressionType.Kind.DATE_TIME, false);
            case TIME -> new ExpressionType(ExpressionType.Kind.TIME, false);
            case UUID -> new ExpressionType(ExpressionType.Kind.UUID, false);
            case DATE_OR_DATE_TIME, TIME_OR_DATE_TIME, TEMPORAL -> ExpressionType.any();
        };
    }
}
