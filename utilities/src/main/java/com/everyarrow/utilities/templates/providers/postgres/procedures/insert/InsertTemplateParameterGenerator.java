/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.templates.providers.postgres.procedures.insert;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

public class InsertTemplateParameterGenerator {

    public static Map<String, String> generateSubstitutions(String tableName, List<String> allColumns, Map<String, String> columnTypes) {
        return generateSubstitutions(tableName, allColumns, columnTypes, Map.of(), Set.of(), Set.of());
    }

    public static Map<String, String> generateSubstitutions(String tableName,
                                                            List<String> allColumns,
                                                            Map<String, String> columnTypes,
                                                            Map<String, String> columnDefaults,
                                                            Set<String> generatedColumns,
                                                            Set<String> identityColumns) {
        Map<String, String> subs = new HashMap<>();
        subs.put("TABLE_NAME", tableName);
        subs.put("TABLE_IDENTIFIER", quoteIdentifier(tableName));
        Set<String> databaseOwnedColumns = databaseOwnedInsertColumns(
                columnDefaults,
                generatedColumns,
                identityColumns);

        // Build comma-separated column names
        StringJoiner namesJoiner = new StringJoiner(", ");
        for (String col : allColumns) {
            if (isDatabaseOwnedColumn(col, databaseOwnedColumns)) {
                continue;
            }
            namesJoiner.add(quoteIdentifier(col));
        }
        subs.put("COLUMN_NAMES", namesJoiner.toString());

        // Build select values expression: "x.col1, x.col2, ..."
        StringJoiner selectJoiner = new StringJoiner(", ");
        for (String col : allColumns) {
            if (isDatabaseOwnedColumn(col, databaseOwnedColumns)) {
                continue;
            }
            selectJoiner.add(selectExpression(col, columnTypes.get(col), columnDefaults));
        }
        subs.put("COLUMN_SELECT_VALUES", selectJoiner.toString());

        // Build column types definition: "col1 text, col2 int, ..."
        StringJoiner typeJoiner = new StringJoiner(", ");
        for (String col : allColumns) {
            if (isDatabaseOwnedColumn(col, databaseOwnedColumns)) {
                continue;
            }
            String type = columnTypes.get(col);
            if (type != null) {
                typeJoiner.add(quoteIdentifier(col) + " " + type);
            }
        }
        subs.put("COLUMN_TYPES", typeJoiner.toString());

        return subs;
    }

    private static Set<String> databaseOwnedInsertColumns(Map<String, String> columnDefaults,
                                                          Set<String> generatedColumns,
                                                          Set<String> identityColumns) {
        Set<String> columns = new HashSet<>();
        if (columnDefaults != null) {
            for (Map.Entry<String, String> entry : columnDefaults.entrySet()) {
                if (isSequenceDefault(entry.getValue())) {
                    columns.add(entry.getKey());
                }
            }
        }

        if (generatedColumns != null) {
            columns.addAll(generatedColumns);
        }

        if (identityColumns != null) {
            columns.addAll(identityColumns);
        }

        return columns;
    }

    private static String selectExpression(String column,
                                           String columnType,
                                           Map<String, String> columnDefaults) {
        String defaultExpression = columnDefaults == null ? null : columnDefaults.get(column);
        if (defaultExpression == null
                || defaultExpression.isBlank()
                || isSequenceDefault(defaultExpression)
                || columnType == null
                || columnType.isBlank()) {
            return "x." + quoteIdentifier(column);
        }
        return "COALESCE(x." + quoteIdentifier(column) + ", (" + defaultExpression + ")::" + columnType + ")";
    }

    private static boolean isDatabaseOwnedColumn(String column, Set<String> databaseOwnedColumns) {
        return databaseOwnedColumns != null && databaseOwnedColumns.contains(column);
    }

    private static boolean isSequenceDefault(String defaultValue) {
        return defaultValue != null
                && defaultValue.toLowerCase(Locale.ROOT).contains("nextval(");
    }

    private static String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
