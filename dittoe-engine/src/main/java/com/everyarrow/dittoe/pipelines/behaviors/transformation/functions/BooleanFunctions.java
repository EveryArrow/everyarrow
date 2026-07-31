package com.everyarrow.dittoe.pipelines.behaviors.transformation.functions;

import com.everyarrow.dittoe.pipelines.behaviors.transformation.util.FunctionUtils;

import java.util.Objects;

/**
 * Provides implementations for boolean functions.
 */
public class BooleanFunctions {
    public static Object eq(Object[] args) {
        if (args.length < 2) throw new RuntimeException("eq requires at least 2 arguments");

        for (int i = 1; i < args.length; i++) {
            if (!Objects.equals(args[i - 1], args[i])) {
                return false;
            }
        }
        return true;
    }

    public static Object lessThan(Object[] args) {
        if (args.length != 2) throw new RuntimeException("lessThan requires 2 arguments");
        return FunctionUtils.toDouble(args[0]) < FunctionUtils.toDouble(args[1]);
    }

    public static Object not(Object[] args) {
        if (args.length != 1) throw new RuntimeException("not requires 1 argument");
        if (!(args[0] instanceof Boolean)) throw new RuntimeException("not: argument must be boolean");
        return !((Boolean) args[0]);
    }

    public static Object and(Object[] args) {
        for (Object arg : args) {
            if (!(arg instanceof Boolean)) throw new RuntimeException("and: all arguments must be boolean");
            if (!((Boolean) arg)) return false;
        }
        return true;
    }

    public static Object or(Object[] args) {
        for (Object arg : args) {
            if (!(arg instanceof Boolean)) throw new RuntimeException("or: all arguments must be boolean");
            if ((Boolean) arg) return true;
        }
        return false;
    }

    public static Object andLazy(LazyArguments args) {
        for (int i = 0; i < args.size(); i++) {
            Object value = args.get(i);
            if (!(value instanceof Boolean booleanValue)) {
                throw new IllegalArgumentException("and: all arguments must be boolean");
            }
            if (!booleanValue) {
                return false;
            }
        }
        return true;
    }

    public static Object orLazy(LazyArguments args) {
        for (int i = 0; i < args.size(); i++) {
            Object value = args.get(i);
            if (!(value instanceof Boolean booleanValue)) {
                throw new IllegalArgumentException("or: all arguments must be boolean");
            }
            if (booleanValue) {
                return true;
            }
        }
        return false;
    }
}
