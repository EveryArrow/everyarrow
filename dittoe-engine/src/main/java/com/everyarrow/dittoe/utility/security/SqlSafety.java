package com.everyarrow.dittoe.utility.security;

import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.Field;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class SqlSafety {
    public static final String ALLOW_UNSAFE_FILTER_EXPRESSION = "allowUnsafeFilterExpression";
    private static final Pattern SAFE_IDENTIFIER_SEGMENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_$-]*");

    private SqlSafety() {
    }

    public static String requireKnownField(String fieldName, List<Field> fields) throws ExecutionException {
        if (fieldName == null || fieldName.isBlank()) {
            throw new ExecutionException("Field name cannot be blank.");
        }
        boolean found = fields.stream().anyMatch(field -> field.getName().equals(fieldName));
        if (!found) {
            throw new ExecutionException("Unknown field in metadata-controlled query condition: " + fieldName);
        }
        return fieldName;
    }

    public static long requireLongLiteral(Object value, String name) throws ExecutionException {
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new ExecutionException("Expected numeric value for " + name + ".");
        }
    }

    public static String quoteSqlLiteral(String value) throws ExecutionException {
        if (value == null) {
            throw new ExecutionException("SQL literal cannot be null.");
        }
        if (value.indexOf('\u0000') >= 0) {
            throw new ExecutionException("SQL literal contains an invalid null byte.");
        }
        return "'" + value.replace("'", "''") + "'";
    }

    public static String quoteBigQueryPath(String... segments) {
        StringBuilder identifier = new StringBuilder("`");
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                identifier.append(".");
            }
            identifier.append(requireSafeIdentifierSegment(segments[i], "BigQuery identifier"));
        }
        return identifier.append("`").toString();
    }

    public static String quoteBigQueryIdentifier(String identifier) {
        return quoteBigQueryPath(identifier);
    }

    public static String quoteSnowflakeIdentifier(String identifier) {
        return "\""
                .concat(requireSafeIdentifierSegment(identifier, "Snowflake identifier")
                        .replace("\"", "\"\""))
                .concat("\"");
    }

    public static String requireSafeIdentifierSegment(String identifier, String name) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank.");
        }
        if (!SAFE_IDENTIFIER_SEGMENT.matcher(identifier).matches()) {
            throw new IllegalArgumentException(name + " contains unsafe characters: " + identifier);
        }
        return identifier;
    }

    public static String getUnsafeFilterExpression(Map<String, String> properties) throws ExecutionException {
        String filterExpression = properties.get("filterExpression");
        if (filterExpression == null || filterExpression.isBlank()) {
            return null;
        }
        if (!Boolean.parseBoolean(properties.getOrDefault(ALLOW_UNSAFE_FILTER_EXPRESSION, "false"))) {
            throw new ExecutionException("Raw filterExpression is disabled by default. Use typed filters or set "
                    + ALLOW_UNSAFE_FILTER_EXPRESSION + "=true only for trusted legacy PMDs.");
        }
        return filterExpression;
    }
}
