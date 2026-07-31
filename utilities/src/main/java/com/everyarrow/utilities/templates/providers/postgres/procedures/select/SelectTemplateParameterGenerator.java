/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.templates.providers.postgres.procedures.select;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public class SelectTemplateParameterGenerator {

    public static Map<String, String> generateSubstitutions(String tableName,
                                                            List<String> allColumns,
                                                            List<String> excludeColumns,
                                                            String primaryKey,
                                                            Map<String, String> columnTypes) {
        Map<String, String> subs = new HashMap<>();
        subs.put("TABLE_NAME", tableName);
        String tableIdentifier = PostgresSelectIdentifiers.quoteIdentifier(tableName);
        subs.put("TABLE_IDENTIFIER", tableIdentifier);
        subs.put(
                "FUNCTION_IDENTIFIER",
                PostgresSelectIdentifiers.functionIdentifier(tableName, "_select_one"));

        StringJoiner colsJoiner = new StringJoiner(", ");
        for (String col : allColumns) {
            if (excludeColumns != null && excludeColumns.contains(col)) {
                continue;
            }
            colsJoiner.add(PostgresSelectIdentifiers.quoteIdentifier(col));
        }
        subs.put("COLUMN_NAMES", colsJoiner.toString());

        String pkType = columnTypes.get(primaryKey);
        if (pkType == null || pkType.isBlank()) {
            throw new IllegalArgumentException(
                    "PostgreSQL type is missing for primary key " + primaryKey);
        }
        subs.put(
                "WHERE_CLAUSE",
                tableIdentifier + "." + PostgresSelectIdentifiers.quoteIdentifier(primaryKey)
                        + " = (p_data->>"
                        + PostgresSelectIdentifiers.quoteJsonKey(primaryKey)
                        + ")::" + pkType);

        return subs;
    }
}
