/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.templates.providers.postgres.procedures.update;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public class UpdateTemplateParameterGenerator {

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

        String whereClause = tableIdentifier + "." + quoteIdentifier(primaryKey)
                + " = x." + quoteIdentifier(primaryKey);
        boolean supportsSoftDelete = hasColumn(allColumns, "isValid");

        StringJoiner colNames = new StringJoiner(", ");
        StringJoiner insertValues = new StringJoiner(", ");
        StringJoiner updateAssignments = new StringJoiner(", ");
        for (String col : allColumns) {
            String columnIdentifier = quoteIdentifier(col);
            colNames.add(columnIdentifier);
            if (col.equalsIgnoreCase("isValid")) {
                insertValues.add("true");
            } else {
                insertValues.add("x." + columnIdentifier);
            }

            if (!col.equalsIgnoreCase(primaryKey)) {
                updateAssignments.add(columnIdentifier + " = x." + columnIdentifier);
            }
        }

        if (supportsSoftDelete) {
            subs.put("UPDATE_STATEMENT", "UPDATE " + tableIdentifier + "\n       SET \"isValid\" = false\n       FROM json_to_recordset(v_data) AS x(" + typeExpr + ")\n       WHERE " + whereClause + ";");
            subs.put("INSERT_STATEMENT", "INSERT INTO " + tableIdentifier + " (" + colNames + ")\n       SELECT " + insertValues + "\n       FROM json_to_recordset(v_data) AS x(" + typeExpr + ");");
            subs.put("SUCCESS_MESSAGE", "Record soft-deleted and new record inserted successfully");
        } else {
            subs.put("UPDATE_STATEMENT", "UPDATE " + tableIdentifier + "\n       SET " + updateAssignments + "\n       FROM json_to_recordset(v_data) AS x(" + typeExpr + ")\n       WHERE " + whereClause + ";");
            subs.put("INSERT_STATEMENT", "");
            subs.put("SUCCESS_MESSAGE", "Record updated successfully");
        }

        return subs;
    }

    private static boolean hasColumn(List<String> allColumns, String columnName) {
        for (String column : allColumns) {
            if (column.equalsIgnoreCase(columnName)) {
                return true;
            }
        }
        return false;
    }

    private static String quoteIdentifier(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
