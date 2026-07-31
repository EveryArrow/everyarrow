package com.everyarrow.dittoe.pipelines.behaviors.transformation.functions;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/** Explicit, null-propagating conversions for PMD-declared computed fields. */
public final class ConversionFunctions {
    private ConversionFunctions() {
    }

    public static Object toStringValue(Object[] args) {
        requireOne("to_string", args);
        return args[0] == null ? null : args[0].toString();
    }

    public static Object toInteger(Object[] args) {
        requireOne("to_integer", args);
        Object value = args[0];
        if (value == null) {
            return null;
        }
        try {
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
            if (value instanceof Number number) {
                return BigDecimal.valueOf(number.doubleValue()).intValueExact();
            }
            if (value instanceof String stringValue) {
                return new BigDecimal(stringValue.trim()).intValueExact();
            }
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("to_integer cannot convert value: " + value, e);
        }
        throw unsupported("to_integer", value);
    }

    public static Object toDouble(Object[] args) {
        requireOne("to_double", args);
        Object value = args[0];
        if (value == null) {
            return null;
        }
        try {
            double converted;
            if (value instanceof Number number) {
                converted = number.doubleValue();
            } else if (value instanceof String stringValue) {
                converted = Double.parseDouble(stringValue.trim());
            } else {
                throw unsupported("to_double", value);
            }
            if (!Double.isFinite(converted)) {
                throw new IllegalArgumentException("to_double does not support non-finite values");
            }
            return converted;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("to_double cannot convert value: " + value, e);
        }
    }

    public static Object toDecimal(Object[] args) {
        requireOne("to_decimal", args);
        Object value = args[0];
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof BigDecimal bigDecimal) {
                return bigDecimal;
            }
            if (value instanceof BigInteger bigInteger) {
                return new BigDecimal(bigInteger);
            }
            if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
                return BigDecimal.valueOf(((Number) value).longValue());
            }
            if (value instanceof Number number) {
                double converted = number.doubleValue();
                if (!Double.isFinite(converted)) {
                    throw new IllegalArgumentException("to_decimal does not support non-finite values");
                }
                return BigDecimal.valueOf(converted);
            }
            if (value instanceof String stringValue) {
                return new BigDecimal(stringValue.trim());
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("to_decimal cannot convert value: " + value, e);
        }
        throw unsupported("to_decimal", value);
    }

    public static Object toBoolean(Object[] args) {
        requireOne("to_boolean", args);
        Object value = args[0];
        if (value == null || value instanceof Boolean) {
            return value;
        }
        if (value instanceof String stringValue) {
            String normalized = stringValue.trim();
            if (normalized.equalsIgnoreCase("true")) {
                return true;
            }
            if (normalized.equalsIgnoreCase("false")) {
                return false;
            }
        }
        throw unsupported("to_boolean", value);
    }

    public static Object toDate(Object[] args) {
        requireOne("to_date", args);
        Object value = args[0];
        if (value == null || value instanceof LocalDate) {
            return value;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toLocalDate();
        }
        if (value instanceof String stringValue) {
            try {
                return LocalDate.parse(stringValue.trim());
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("to_date expects an ISO-8601 date", e);
            }
        }
        throw unsupported("to_date", value);
    }

    public static Object toDateTime(Object[] args) {
        requireOne("to_datetime", args);
        Object value = args[0];
        if (value == null || value instanceof LocalDateTime) {
            return value;
        }
        if (value instanceof LocalDate date) {
            return date.atStartOfDay();
        }
        if (value instanceof String stringValue) {
            try {
                return LocalDateTime.parse(stringValue.trim());
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("to_datetime expects an ISO-8601 local date-time", e);
            }
        }
        throw unsupported("to_datetime", value);
    }

    public static Object toTime(Object[] args) {
        requireOne("to_time", args);
        Object value = args[0];
        if (value == null || value instanceof LocalTime) {
            return value;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toLocalTime();
        }
        if (value instanceof String stringValue) {
            try {
                return LocalTime.parse(stringValue.trim());
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("to_time expects an ISO-8601 local time", e);
            }
        }
        throw unsupported("to_time", value);
    }

    public static Object toUuid(Object[] args) {
        requireOne("to_uuid", args);
        Object value = args[0];
        if (value == null || value instanceof UUID) {
            return value;
        }
        if (value instanceof String stringValue) {
            try {
                return UUID.fromString(stringValue.trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("to_uuid expects a standard UUID string", e);
            }
        }
        throw unsupported("to_uuid", value);
    }

    public static Object isNull(Object[] args) {
        requireOne("is_null", args);
        return args[0] == null;
    }

    public static Object isNotNull(Object[] args) {
        requireOne("is_not_null", args);
        return args[0] != null;
    }

    private static void requireOne(String name, Object[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException(name + " requires exactly one argument");
        }
    }

    private static IllegalArgumentException unsupported(String functionName, Object value) {
        return new IllegalArgumentException(
                functionName + " cannot convert " + value.getClass().getSimpleName());
    }
}
