package com.everyarrow.dittoe.pipelines.behaviors.schemavalidation;

/**
 * Utility class for Schema Validation.
 */
public class SchemaValidationUtils {

    /**
     * Private constructor. Prevents instantiation of a utility class.
     */
    private SchemaValidationUtils() {
    }

    /**
     * Concatenates two nullable Strings.
     *
     * @param str1 First String.
     * @param str2 Second String.
     * @return Concatenation of the two Strings.
     */
    public static String concat(String str1, String str2) {
        if (str1 == null && str2 == null)
            return null;
        else if (str1 == null)
            return str2;
        else if (str2 == null)
            return str1;
        return String.format("%s, %s", str1, str2);
    }
}
