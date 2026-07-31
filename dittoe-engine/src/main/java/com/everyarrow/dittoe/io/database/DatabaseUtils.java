package com.everyarrow.dittoe.io.database;

import org.everyarrow.dittoe.schema.Field;
import org.everyarrow.dittoe.schema.SourceToTargetMapping;

import java.util.List;

/**
 * Utility class for Database.
 */
public class DatabaseUtils {
    /**
     * Constant for property key "databaseType".
     */


    /**
     * Private constructor. It prevents instantiation of Utility class.
     */
    private DatabaseUtils() {
    }

    /**
     * Puts single quotes around a string.
     *
     * @param string Input string.
     * @return String object enclosed by single quotes.
     */
    public static String singleQuote(String string) {
        return "'".concat(string).concat("'");
    }

    public static List<Field> getTargetFields(List<SourceToTargetMapping> sourceToTargetMappings) {
        return sourceToTargetMappings.stream().map(SourceToTargetMapping::getTargetField).toList();
    }
}
