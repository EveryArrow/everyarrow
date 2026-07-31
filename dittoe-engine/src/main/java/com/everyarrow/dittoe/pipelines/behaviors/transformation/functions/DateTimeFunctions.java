package com.everyarrow.dittoe.pipelines.behaviors.transformation.functions;

import com.everyarrow.dittoe.pipelines.behaviors.transformation.util.FunctionUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Provides implementations for date/time functions.
 */
public class DateTimeFunctions {
    public static Object currentDateTime(Object[] args) {
        return LocalDateTime.now();
    }

    public static Object currentDate(Object[] args) {
        return LocalDate.now();
    }

    public static Object currentTime(Object[] args) {
        return LocalTime.now();
    }

    public static Object year(Object[] args) {
        if (args.length != 1) throw new RuntimeException("year requires 1 argument");
        Object date = args[0];
        if (date instanceof LocalDate localDate) {
            return localDate.getYear();
        } else if (date instanceof LocalDateTime localDateTime) {
            return localDateTime.getYear();
        }
        throw new RuntimeException("year: unsupported type " + date.getClass().getSimpleName());
    }

    public static Object month(Object[] args) {
        if (args.length != 1) throw new RuntimeException("month requires 1 argument");
        Object date = args[0];
        if (date instanceof LocalDate localDate) {
            return localDate.getMonthValue();
        } else if (date instanceof LocalDateTime localDateTime) {
            return localDateTime.getMonthValue();
        }
        throw new RuntimeException("month: unsupported type " + date.getClass().getSimpleName());
    }

    public static Object day(Object[] args) {
        if (args.length != 1) throw new RuntimeException("day requires 1 argument");
        Object date = args[0];
        if (date instanceof LocalDate localDate) {
            return localDate.getDayOfMonth();
        } else if (date instanceof LocalDateTime localDateTime) {
            return localDateTime.getDayOfMonth();
        }
        throw new RuntimeException("day: unsupported type " + date.getClass().getSimpleName());
    }

    public static Object hour(Object[] args) {
        if (args.length != 1) throw new RuntimeException("hour requires 1 argument");
        Object time = args[0];
        if (time instanceof LocalTime localTime) {
            return localTime.getHour();
        } else if (time instanceof LocalDateTime localDateTime) {
            return localDateTime.getHour();
        }
        throw new RuntimeException("hour: unsupported type " + time.getClass().getSimpleName());
    }

    public static Object minute(Object[] args) {
        if (args.length != 1) throw new RuntimeException("minute requires 1 argument");
        Object time = args[0];
        if (time instanceof LocalTime localTime) {
            return localTime.getMinute();
        } else if (time instanceof LocalDateTime localDateTime) {
            return localDateTime.getMinute();
        }
        throw new RuntimeException("minute: unsupported type " + time.getClass().getSimpleName());
    }

    public static Object second(Object[] args) {
        if (args.length != 1) throw new RuntimeException("second requires 1 argument");
        Object time = args[0];
        if (time instanceof LocalTime localTime) {
            return localTime.getSecond();
        } else if (time instanceof LocalDateTime localDateTime) {
            return localDateTime.getSecond();
        }
        throw new RuntimeException("second: unsupported type " + time.getClass().getSimpleName());
    }

    public static Object dateAdd(Object[] args) {
        if (args.length != 2) throw new RuntimeException("date_add requires 2 arguments");
        if (!(args[0] instanceof LocalDate))
            throw new RuntimeException("date_add: first argument must be a LocalDate");
        int days = FunctionUtils.toInt(args[1]);
        return ((LocalDate) args[0]).plusDays(days);
    }

    public static Object dateSub(Object[] args) {
        if (args.length != 2) throw new RuntimeException("date_sub requires 2 arguments");
        if (!(args[0] instanceof LocalDate))
            throw new RuntimeException("date_sub: first argument must be a LocalDate");
        int days = FunctionUtils.toInt(args[1]);
        return ((LocalDate) args[0]).minusDays(days);
    }

    public static Object extract(Object[] args) {
        if (args.length != 2) throw new RuntimeException("extract requires 2 arguments");
        String field = ((String) args[0]).toLowerCase();
        Object date = args[1];
        return switch (field) {
            case "year" -> year(new Object[]{date});
            case "month" -> month(new Object[]{date});
            case "day" -> day(new Object[]{date});
            case "hour" -> hour(new Object[]{date});
            case "minute" -> minute(new Object[]{date});
            case "second" -> second(new Object[]{date});
            default -> throw new RuntimeException("extract: unsupported field " + field);
        };
    }
}
