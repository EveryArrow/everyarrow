package com.everyarrow.dittoe.pipelines.behaviors.transformation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.UUID;

/** Value types used by DEAL function signatures. */
public enum FunctionValueType {
    ANY,
    NUMBER,
    DOUBLE,
    DECIMAL,
    INTEGER,
    STRING,
    BOOLEAN,
    DATE,
    DATE_TIME,
    TIME,
    DATE_OR_DATE_TIME,
    TIME_OR_DATE_TIME,
    TEMPORAL,
    UUID;

    boolean accepts(Object value) {
        if (this == ANY) {
            return true;
        }
        if (value == null) {
            return false;
        }
        return switch (this) {
            case ANY -> true;
            case NUMBER -> value instanceof Number;
            case DOUBLE -> value instanceof Double;
            case DECIMAL -> value instanceof BigDecimal;
            case INTEGER -> isExactInteger(value);
            case STRING -> value instanceof String;
            case BOOLEAN -> value instanceof Boolean;
            case DATE -> value instanceof LocalDate;
            case DATE_TIME -> value instanceof LocalDateTime;
            case TIME -> value instanceof LocalTime;
            case DATE_OR_DATE_TIME -> value instanceof LocalDate || value instanceof LocalDateTime;
            case TIME_OR_DATE_TIME -> value instanceof LocalTime || value instanceof LocalDateTime;
            case TEMPORAL -> value instanceof LocalDate || value instanceof LocalDateTime || value instanceof LocalTime;
            case UUID -> value instanceof UUID;
        };
    }

    private static boolean isExactInteger(Object value) {
        try {
            if (value instanceof Byte || value instanceof Short || value instanceof Integer) {
                return true;
            }
            if (value instanceof Long longValue) {
                Math.toIntExact(longValue);
                return true;
            }
            if (value instanceof BigInteger bigInteger) {
                bigInteger.intValueExact();
                return true;
            }
            if (value instanceof BigDecimal bigDecimal) {
                bigDecimal.intValueExact();
                return true;
            }
            if (value instanceof Float floatValue) {
                if (!Float.isFinite(floatValue)) {
                    return false;
                }
                BigDecimal.valueOf(floatValue.doubleValue()).intValueExact();
                return true;
            }
            if (value instanceof Double doubleValue) {
                if (!Double.isFinite(doubleValue)) {
                    return false;
                }
                BigDecimal.valueOf(doubleValue).intValueExact();
                return true;
            }
        } catch (ArithmeticException ignored) {
            return false;
        }
        return false;
    }
}
