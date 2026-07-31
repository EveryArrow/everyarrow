package com.everyarrow.dittoe.pipelines.behaviors.transformation.functions;

import com.everyarrow.dittoe.pipelines.behaviors.transformation.util.FunctionUtils;

public class NumericFunctions {

    /**
     * Checks if all objects in the array are instances of Integer.
     */
    private static boolean allIntegers(Object[] args) {
        for (Object arg : args) {
            if (!(arg instanceof Integer)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the sum of the provided arguments.
     * If all arguments are integers, returns an integer sum; otherwise, returns a double.
     */
    public static Object sum(Object[] args) {
        if (args.length == 0) throw new RuntimeException("sum requires at least one argument");
        if (allIntegers(args)) {
            int total = 0;
            for (Object arg : args) {
                total += (Integer) arg;
            }
            return total;
        } else {
            double total = 0.0;
            for (Object arg : args) {
                total += FunctionUtils.toDouble(arg);
            }
            return total;
        }
    }

    /**
     * Returns the product of the provided arguments.
     * If all arguments are integers, returns an integer product; otherwise, returns a double.
     */
    public static Object multiply(Object[] args) {
        if (args.length == 0) throw new RuntimeException("multiply requires at least one argument");
        if (allIntegers(args)) {
            int product = 1;
            for (Object arg : args) {
                product *= (Integer) arg;
            }
            return product;
        } else {
            double product = 1.0;
            for (Object arg : args) {
                product *= FunctionUtils.toDouble(arg);
            }
            return product;
        }
    }

    public static Object abs(Object[] args) {
        if (args.length != 1) throw new RuntimeException("abs requires 1 argument");
        Object arg = args[0];
        if (arg instanceof Integer) {
            return Math.abs((Integer) arg);
        } else {
            return Math.abs(FunctionUtils.toDouble(arg));
        }
    }

    public static Object acos(Object[] args) {
        if (args.length != 1) throw new RuntimeException("acos requires 1 argument");
        return Math.acos(FunctionUtils.toDouble(args[0]));
    }

    public static Object asin(Object[] args) {
        if (args.length != 1) throw new RuntimeException("asin requires 1 argument");
        return Math.asin(FunctionUtils.toDouble(args[0]));
    }

    public static Object atan(Object[] args) {
        if (args.length != 1) throw new RuntimeException("atan requires 1 argument");
        return Math.atan(FunctionUtils.toDouble(args[0]));
    }

    public static Object atan2(Object[] args) {
        if (args.length != 2) throw new RuntimeException("atan2 requires 2 arguments");
        return Math.atan2(FunctionUtils.toDouble(args[0]), FunctionUtils.toDouble(args[1]));
    }

    public static Object ceiling(Object[] args) {
        if (args.length != 1) throw new RuntimeException("ceiling requires 1 argument");
        double result = Math.ceil(FunctionUtils.toDouble(args[0]));
        // If input was an integer, return an int if possible.
        if (args[0] instanceof Integer && result == (int) result) {
            return (int) result;
        }
        return result;
    }

    public static Object cos(Object[] args) {
        if (args.length != 1) throw new RuntimeException("cos requires 1 argument");
        return Math.cos(FunctionUtils.toDouble(args[0]));
    }

    public static Object cot(Object[] args) {
        if (args.length != 1) throw new RuntimeException("cot requires 1 argument");
        double angle = FunctionUtils.toDouble(args[0]);
        double sine = Math.sin(angle);
        if (sine == 0) throw new ArithmeticException("cotangent undefined for angle with sine 0");
        return Math.cos(angle) / sine;
    }

    public static Object degrees(Object[] args) {
        if (args.length != 1) throw new RuntimeException("degrees requires 1 argument");
        return Math.toDegrees(FunctionUtils.toDouble(args[0]));
    }

    public static Object exp(Object[] args) {
        if (args.length != 1) throw new RuntimeException("exp requires 1 argument");
        return Math.exp(FunctionUtils.toDouble(args[0]));
    }

    public static Object floor(Object[] args) {
        if (args.length != 1) throw new RuntimeException("floor requires 1 argument");
        double result = Math.floor(FunctionUtils.toDouble(args[0]));
        if (args[0] instanceof Integer && result == (int) result) {
            return (int) result;
        }
        return result;
    }

    public static Object log(Object[] args) {
        if (args.length != 1) throw new RuntimeException("log requires 1 argument");
        return Math.log(FunctionUtils.toDouble(args[0]));
    }

    public static Object log10(Object[] args) {
        if (args.length != 1) throw new RuntimeException("log10 requires 1 argument");
        return Math.log10(FunctionUtils.toDouble(args[0]));
    }

    public static Object mod(Object[] args) {
        if (args.length != 2) throw new RuntimeException("mod requires 2 arguments");
        if (args[0] instanceof Integer && args[1] instanceof Integer) {
            int divisor = (Integer) args[1];
            if (divisor == 0) throw new ArithmeticException("Division by zero in mod");
            return (Integer) args[0] % divisor;
        } else {
            double dividend = FunctionUtils.toDouble(args[0]);
            double divisor = FunctionUtils.toDouble(args[1]);
            if (divisor == 0) throw new ArithmeticException("Division by zero in mod");
            return dividend % divisor;
        }
    }

    public static Object power(Object[] args) {
        if (args.length != 2) throw new RuntimeException("power requires 2 arguments");
        double result = Math.pow(FunctionUtils.toDouble(args[0]), FunctionUtils.toDouble(args[1]));
        // If both arguments are integers and the result is whole, return an integer.
        if (args[0] instanceof Integer && args[1] instanceof Integer && result == (int) result) {
            return (int) result;
        }
        return result;
    }

    public static Object radians(Object[] args) {
        if (args.length != 1) throw new RuntimeException("radians requires 1 argument");
        return Math.toRadians(FunctionUtils.toDouble(args[0]));
    }

    public static Object round(Object[] args) {
        if (args.length != 1 && args.length != 2)
            throw new RuntimeException("round requires 1 or 2 arguments");
        // If rounding an integer with one argument, just return it.
        if (args[0] instanceof Integer && args.length == 1) {
            return args[0];
        } else if (args[0] instanceof Integer && args.length == 2 && args[1] instanceof Integer) {
            return args[0];
        } else {
            double value = FunctionUtils.toDouble(args[0]);
            if (args.length == 1) {
                double rounded = Math.round(value);
                if (rounded == (int) rounded) {
                    return (int) rounded;
                }
                return rounded;
            } else {
                int decimals = FunctionUtils.toInt(args[1]);
                double scale = Math.pow(10, decimals);
                double rounded = Math.round(value * scale) / scale;
                if (decimals == 0 && rounded == (int) rounded) {
                    return (int) rounded;
                }
                return rounded;
            }
        }
    }

    public static Object sign(Object[] args) {
        if (args.length != 1) throw new RuntimeException("sign requires 1 argument");
        double value = FunctionUtils.toDouble(args[0]);
        double s = Math.signum(value);
        if (args[0] instanceof Integer && s == (int) s) {
            return (int) s;
        }
        return s;
    }

    public static Object sin(Object[] args) {
        if (args.length != 1) throw new RuntimeException("sin requires 1 argument");
        return Math.sin(FunctionUtils.toDouble(args[0]));
    }

    public static Object sqrt(Object[] args) {
        if (args.length != 1) throw new RuntimeException("sqrt requires 1 argument");
        double result = Math.sqrt(FunctionUtils.toDouble(args[0]));
        if (args[0] instanceof Integer && result == (int) result) {
            return (int) result;
        }
        return result;
    }

    public static Object tan(Object[] args) {
        if (args.length != 1) throw new RuntimeException("tan requires 1 argument");
        return Math.tan(FunctionUtils.toDouble(args[0]));
    }

    public static Object greatest(Object[] args) {
        if (args.length == 0) throw new RuntimeException("greatest requires at least one argument");
        if (allIntegers(args)) {
            int max = (Integer) args[0];
            for (int i = 1; i < args.length; i++) {
                int value = (Integer) args[i];
                if (value > max) {
                    max = value;
                }
            }
            return max;
        } else {
            double max = FunctionUtils.toDouble(args[0]);
            for (int i = 1; i < args.length; i++) {
                double value = FunctionUtils.toDouble(args[i]);
                if (value > max) {
                    max = value;
                }
            }
            if (max == (int) max) {
                return (int) max;
            }
            return max;
        }
    }

    public static Object least(Object[] args) {
        if (args.length == 0) throw new RuntimeException("least requires at least one argument");
        if (allIntegers(args)) {
            int min = (Integer) args[0];
            for (int i = 1; i < args.length; i++) {
                int value = (Integer) args[i];
                if (value < min) {
                    min = value;
                }
            }
            return min;
        } else {
            double min = FunctionUtils.toDouble(args[0]);
            for (int i = 1; i < args.length; i++) {
                double value = FunctionUtils.toDouble(args[i]);
                if (value < min) {
                    min = value;
                }
            }
            if (min == (int) min) {
                return (int) min;
            }
            return min;
        }
    }

    public static Object trunc(Object[] args) {
        if (args.length != 1 && args.length != 2)
            throw new RuntimeException("trunc requires 1 or 2 arguments");
        if (args[0] instanceof Integer && (args.length == 1 || args[1] instanceof Integer)) {
            return args[0]; // Already integer
        } else {
            double value = FunctionUtils.toDouble(args[0]);
            if (args.length == 1) {
                double truncated = (int) value;
                if (truncated == (int) truncated) {
                    return (int) truncated;
                }
                return truncated;
            } else {
                int decimals = FunctionUtils.toInt(args[1]);
                double scale = Math.pow(10, decimals);
                double truncated = Math.floor(value * scale) / scale;
                if (decimals == 0 && truncated == (int) truncated) {
                    return (int) truncated;
                }
                return truncated;
            }
        }
    }
}
