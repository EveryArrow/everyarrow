package com.everyarrow.dittoe.pipelines.behaviors.transformation.functions;

import java.util.Objects;

/**
 * Provides implementations for conditional and null-handling functions.
 */
public class ConditionalFunctions {
    public static Object coalesce(Object[] args) {
        for (Object arg : args) {
            if (arg != null) return arg;
        }
        return null;
    }

    public static Object nullif(Object[] args) {
        if (args.length != 2) throw new RuntimeException("nullif requires 2 arguments");
        return Objects.equals(args[0], args[1]) ? null : args[0];
    }

    public static Object ifFunc(Object[] args) {
        if (args.length < 3) {
            throw new RuntimeException("if requires at least 3 arguments: condition, thenExpr, elseExpr");
        }
        int pairsCount = args.length - 1;
        if (pairsCount % 2 != 0) {
            throw new RuntimeException("if: Must have an even number of arguments before the final elseValue.");
        }
        Object elseValue = args[args.length - 1];
        for (int i = 0; i < pairsCount; i += 2) {
            if (!(args[i] instanceof Boolean)) {
                throw new RuntimeException("if: condition must be boolean at index " + i);
            }
            if ((Boolean) args[i]) {
                return args[i + 1];
            }
        }
        return elseValue;
    }

    public static Object caseWhen(Object[] args) {
        if (args.length < 3) {
            throw new RuntimeException("caseWhen requires at least 3 arguments");
        }
        int pairsCount = args.length - 1;
        if (pairsCount % 2 != 0) {
            throw new RuntimeException("caseWhen: Must have an even number of arguments before the final default.");
        }
        Object defaultValue = args[args.length - 1];
        for (int i = 0; i < pairsCount; i += 2) {
            if (!(args[i] instanceof Boolean)) {
                throw new RuntimeException("caseWhen: condition must be boolean at index " + i);
            }
            if ((Boolean) args[i]) {
                return args[i + 1];
            }
        }
        return defaultValue;
    }

    public static Object coalesceLazy(LazyArguments args) {
        for (int i = 0; i < args.size(); i++) {
            Object value = args.get(i);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    public static Object ifLazy(LazyArguments args) {
        return conditionalLazy("if", args);
    }

    public static Object caseWhenLazy(LazyArguments args) {
        return conditionalLazy("caseWhen", args);
    }

    private static Object conditionalLazy(String functionName, LazyArguments args) {
        int pairsCount = args.size() - 1;
        for (int i = 0; i < pairsCount; i += 2) {
            Object condition = args.get(i);
            if (!(condition instanceof Boolean booleanCondition)) {
                throw new IllegalArgumentException(
                        functionName + ": condition must be boolean at index " + i);
            }
            if (booleanCondition) {
                return args.get(i + 1);
            }
        }
        return args.get(args.size() - 1);
    }
}
