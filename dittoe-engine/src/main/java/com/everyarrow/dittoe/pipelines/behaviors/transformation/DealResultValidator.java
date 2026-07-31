package com.everyarrow.dittoe.pipelines.behaviors.transformation;

import com.fasterxml.jackson.databind.JsonNode;
import org.w3c.dom.Node;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Enforces the PMD-derived type and nullability contract for computed DEAL fields.
 */
final class DealResultValidator {
    private static final long MAX_EXACT_DOUBLE_INTEGER = 9_007_199_254_740_992L;

    Object validate(DealSchema.FieldDefinition field, Object value, String expression, int rowIndex)
            throws ExpressionEvaluationException {
        if (value == null) {
            if (field.nullable()) {
                return null;
            }
            throw invalidResult(field, value, expression, rowIndex, "result is null for a non-nullable field");
        }

        try {
            return switch (field.dataType()) {
                case STRING -> requireInstance(field, value, String.class);
                case INTEGER -> toInteger(value);
                case DOUBLE -> toDouble(value);
                case DECIMAL -> toDecimal(value);
                case BOOLEAN -> requireInstance(field, value, Boolean.class);
                case DATE -> requireInstance(field, value, LocalDate.class);
                case DATE_TIME -> requireInstance(field, value, LocalDateTime.class);
                case TIME -> requireInstance(field, value, LocalTime.class);
                case UUID -> requireInstance(field, value, UUID.class);
                case TIME_WITH_TIME_ZONE -> requireInstance(field, value, OffsetTime.class);
                case DATE_TIME_WITH_TIME_ZONE -> requireInstance(field, value, OffsetDateTime.class);
                case JSON, JSONB -> requireJsonValue(field, value);
                case ARRAY -> requireArrayValue(field, value);
                case NUMBER -> requireFiniteNumber(value);
                case BYTES -> requireInstance(field, value, byte[].class);
                case XML -> requireXmlValue(field, value);
            };
        } catch (IllegalArgumentException | ArithmeticException e) {
            throw invalidResult(field, value, expression, rowIndex, e.getMessage(), e);
        }
    }

    private static Object requireInstance(DealSchema.FieldDefinition field, Object value, Class<?> expectedType) {
        if (!expectedType.isInstance(value)) {
            throw new IllegalArgumentException(
                    "expected " + field.dataType() + " but received " + value.getClass().getSimpleName());
        }
        return value;
    }

    private static Integer toInteger(Object value) {
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof Byte byteValue) {
            return byteValue.intValue();
        }
        if (value instanceof Short shortValue) {
            return shortValue.intValue();
        }
        if (value instanceof Long longValue) {
            return Math.toIntExact(longValue);
        }
        if (value instanceof BigInteger bigInteger) {
            return bigInteger.intValueExact();
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal.intValueExact();
        }
        if (value instanceof Float floatValue) {
            return finiteDecimal(floatValue.doubleValue()).intValueExact();
        }
        if (value instanceof Double doubleValue) {
            return finiteDecimal(doubleValue).intValueExact();
        }
        throw new IllegalArgumentException("expected an exact integer-compatible number");
    }

    private static Double toDouble(Object value) {
        if (value instanceof Double doubleValue) {
            return requireFiniteDouble(doubleValue);
        }
        if (value instanceof Float floatValue) {
            return requireFiniteDouble(floatValue.doubleValue());
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof Long longValue) {
            if (longValue > MAX_EXACT_DOUBLE_INTEGER || longValue < -MAX_EXACT_DOUBLE_INTEGER) {
                throw new ArithmeticException("integer value cannot be represented exactly as DOUBLE");
            }
            return longValue.doubleValue();
        }
        if (value instanceof BigInteger bigInteger) {
            if (bigInteger.abs().compareTo(BigInteger.valueOf(MAX_EXACT_DOUBLE_INTEGER)) > 0) {
                throw new ArithmeticException("integer value cannot be represented exactly as DOUBLE");
            }
            return bigInteger.doubleValue();
        }
        if (value instanceof BigDecimal bigDecimal) {
            double converted = requireFiniteDouble(bigDecimal.doubleValue());
            if (BigDecimal.valueOf(converted).compareTo(bigDecimal.stripTrailingZeros()) != 0) {
                throw new ArithmeticException("decimal value cannot be represented exactly as DOUBLE");
            }
            return converted;
        }
        throw new IllegalArgumentException("expected a number compatible with DOUBLE");
    }

    private static BigDecimal toDecimal(Object value) {
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof BigInteger bigInteger) {
            return new BigDecimal(bigInteger);
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return BigDecimal.valueOf(((Number) value).longValue());
        }
        if (value instanceof Float floatValue) {
            return finiteDecimal(floatValue.doubleValue());
        }
        if (value instanceof Double doubleValue) {
            return finiteDecimal(doubleValue);
        }
        throw new IllegalArgumentException("expected a number compatible with DECIMAL");
    }

    private static Number requireFiniteNumber(Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("expected NUMBER but received " + value.getClass().getSimpleName());
        }
        if (number instanceof Double doubleValue) {
            requireFiniteDouble(doubleValue);
        } else if (number instanceof Float floatValue) {
            requireFiniteDouble(floatValue.doubleValue());
        }
        return number;
    }

    private static Object requireJsonValue(DealSchema.FieldDefinition field, Object value) {
        if (value instanceof Map<?, ?> || value instanceof List<?> || value instanceof JsonNode || value instanceof String) {
            return value;
        }
        throw new IllegalArgumentException(
                "expected " + field.dataType() + " object, array, JSON node, or serialized string");
    }

    private static Object requireArrayValue(DealSchema.FieldDefinition field, Object value) {
        if (value instanceof List<?> || value.getClass().isArray()) {
            return value;
        }
        throw new IllegalArgumentException(
                "expected " + field.dataType() + " but received " + value.getClass().getSimpleName());
    }

    private static Object requireXmlValue(DealSchema.FieldDefinition field, Object value) {
        if (value instanceof String || value instanceof Node) {
            return value;
        }
        throw new IllegalArgumentException(
                "expected " + field.dataType() + " document or serialized string");
    }

    private static BigDecimal finiteDecimal(double value) {
        requireFiniteDouble(value);
        return BigDecimal.valueOf(value);
    }

    private static double requireFiniteDouble(double value) {
        if (!Double.isFinite(value)) {
            throw new ArithmeticException("non-finite numeric result is not supported");
        }
        return value;
    }

    private static ExpressionEvaluationException invalidResult(DealSchema.FieldDefinition field,
                                                               Object value,
                                                               String expression,
                                                               int rowIndex,
                                                               String detail) {
        return invalidResult(field, value, expression, rowIndex, detail, null);
    }

    private static ExpressionEvaluationException invalidResult(DealSchema.FieldDefinition field,
                                                               Object value,
                                                               String expression,
                                                               int rowIndex,
                                                               String detail,
                                                               Throwable cause) {
        String actualType = value == null ? "null" : value.getClass().getSimpleName();
        String message = "Invalid DEAL result for field '" + field.name() + "' at row " + rowIndex
                + ": expected " + field.dataType() + (field.nullable() ? " (nullable)" : " (non-nullable)")
                + ", actual " + actualType + ", expression '" + expression + "': " + detail;
        return cause == null
                ? new ExpressionEvaluationException(message)
                : new ExpressionEvaluationException(message, cause);
    }
}
