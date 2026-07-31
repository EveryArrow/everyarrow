package com.everyarrow.dittoe.pipelines.behaviors.transformation.util;

/**
 * Utility class for converting function arguments.
 */
public class FunctionUtils {
    /**
     * Converts an object to a double.
     *
     * @param o the object to convert.
     * @return the double value.
     * @throws IllegalArgumentException if the object is null or not a number.
     */
    public static double toDouble(Object o) {
        if (o == null) {
            throw new IllegalArgumentException("Null value provided for numeric conversion.");
        }
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        throw new IllegalArgumentException("Expected a numeric argument, got: " + o.getClass().getSimpleName());
    }

    /**
     * Converts an object to an integer.
     *
     * @param o the object to convert.
     * @return the integer value.
     * @throws IllegalArgumentException if the object is null or not a number.
     */
    public static int toInt(Object o) {
        if (o == null) {
            throw new IllegalArgumentException("Null value provided for integer conversion.");
        }
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        throw new IllegalArgumentException("Expected an integer argument, got: " + o.getClass().getSimpleName());
    }
}
