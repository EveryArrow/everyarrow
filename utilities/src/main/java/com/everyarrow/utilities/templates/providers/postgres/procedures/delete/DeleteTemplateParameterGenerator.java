/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.templates.providers.postgres.procedures.delete;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public class DeleteTemplateParameterGenerator {

    public static Map<String, String> generateSubstitutions(String tableName,
                                                            List<String> allColumns,
                                                            String primaryKey,
                                                            Map<String, String> columnTypes) {
        Map<String, String> subs = new HashMap<>();
        subs.put("TABLE_NAME", tableName);
        String tableIdentifier = quoteIdentifier(tableName);

        StringJoiner typeExpr = new StringJoiner(", ");
        for (String col : allColumns) {
            String type = columnTypes.get(col);
            if (type != null) {
                typeExpr.add(quoteIdentifier(col) + " " + type);
            }
        }
        subs.put("COLUMN_TYPES", typeExpr.toString());

        // WHERE clause uses the primary key extracted from p_data JSON object.
        String pkType = columnTypes.get(primaryKey);
        String whereClause = tableIdentifier + "." + quoteIdentifier(primaryKey)
                + " = (p_data->>'" + primaryKey.replace("'", "''") + "')::" + pkType;
        String softDeleteColumn = firstMatchingColumn(allColumns, "is_active_row", "is_active", "isValid");

        if (softDeleteColumn != null) {
            subs.put("DELETE_STATEMENT", "UPDATE " + tableIdentifier + "\n       SET "
                    + quoteIdentifier(softDeleteColumn) + " = false\n       WHERE " + whereClause + ";");
            subs.put("SUCCESS_MESSAGE", "Record soft-deleted successfully");
        } else {
            subs.put("DELETE_STATEMENT", "DELETE FROM " + tableIdentifier + "\n       WHERE " + whereClause + ";");
            subs.put("SUCCESS_MESSAGE", "Record deleted successfully");
        }

        return subs;
    }

    private static String firstMatchingColumn(List<String> allColumns, String... columnNames) {
        for (String columnName : columnNames) {
            for (String column : allColumns) {
                if (column.equalsIgnoreCase(columnName)) {
                    return column;
                }
            }
        }
        return null;
    }

    private static String quoteIdentifier(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
