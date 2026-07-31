/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.db;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MetadataUtils {

    public static class ColumnMetadata {
        private final String name;
        private final String typeName;
        private final String defaultValue;
        private final boolean generatedColumn;
        private final boolean identityColumn;

        public ColumnMetadata(String name,
                              String typeName,
                              String defaultValue,
                              boolean generatedColumn,
                              boolean identityColumn) {
            this.name = name;
            this.typeName = typeName;
            this.defaultValue = defaultValue;
            this.generatedColumn = generatedColumn;
            this.identityColumn = identityColumn;
        }

        public String getName() {
            return name;
        }

        public String getTypeName() {
            return typeName;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        public boolean isGeneratedColumn() {
            return generatedColumn;
        }

        public boolean isIdentityColumn() {
            return identityColumn;
        }

        public boolean hasDefaultValue() {
            return defaultValue != null && !defaultValue.isBlank();
        }

        public boolean hasSequenceDefault() {
            return isSequenceDefault(defaultValue);
        }

        public boolean isDatabaseOwnedOnInsert() {
            return generatedColumn || identityColumn || hasSequenceDefault();
        }
    }

    public static class TableMetadata {
        private final List<String> allColumns;
        private final List<String> primaryKeys;
        private final Map<String, String> columnTypes;
        private final Map<String, ColumnMetadata> columnsByName;

        public TableMetadata(List<String> allColumns, List<String> primaryKeys, Map<String, String> columnTypes) {
            this(allColumns, primaryKeys, columnTypes, defaultColumnMetadata(allColumns, columnTypes));
        }

        public TableMetadata(List<String> allColumns,
                             List<String> primaryKeys,
                             Map<String, String> columnTypes,
                             Map<String, ColumnMetadata> columnsByName) {
            this.allColumns = allColumns;
            this.primaryKeys = primaryKeys;
            this.columnTypes = columnTypes;
            this.columnsByName = columnsByName;
        }

        public List<String> getAllColumns() {
            return allColumns;
        }

        public List<String> getPrimaryKeys() {
            return primaryKeys;
        }

        public Map<String, String> getColumnTypes() {
            return columnTypes;
        }

        public Map<String, ColumnMetadata> getColumnsByName() {
            return columnsByName;
        }

        public Map<String, String> getColumnDefaults() {
            Map<String, String> defaults = new HashMap<>();
            for (ColumnMetadata column : columnsByName.values()) {
                if (column.getDefaultValue() != null && !column.getDefaultValue().isBlank()) {
                    defaults.put(column.getName(), column.getDefaultValue());
                }
            }
            return defaults;
        }

        public Set<String> getGeneratedColumns() {
            Set<String> generated = new HashSet<>();
            for (ColumnMetadata column : columnsByName.values()) {
                if (column.isGeneratedColumn()) {
                    generated.add(column.getName());
                }
            }
            return generated;
        }

        public Set<String> getIdentityColumns() {
            Set<String> identity = new HashSet<>();
            for (ColumnMetadata column : columnsByName.values()) {
                if (column.isIdentityColumn()) {
                    identity.add(column.getName());
                }
            }
            return identity;
        }

        public Set<String> getDatabaseOwnedInsertColumns() {
            Set<String> databaseOwned = new HashSet<>();
            for (ColumnMetadata column : columnsByName.values()) {
                if (column.isDatabaseOwnedOnInsert()) {
                    databaseOwned.add(column.getName());
                }
            }
            return databaseOwned;
        }

        public List<String> getInsertInputColumns() {
            Set<String> databaseOwned = getDatabaseOwnedInsertColumns();
            List<String> insertColumns = new ArrayList<>();
            for (String column : allColumns) {
                if (!databaseOwned.contains(column)) {
                    insertColumns.add(column);
                }
            }
            return insertColumns;
        }
    }

    /**
     * Retrieves metadata for the given table including all columns, primary keys, and column types.
     *
     * @param conn      An open JDBC SimpleConnection.
     * @param schema    The schema name (can be null for default).
     * @param tableName The table name.
     * @return A TableMetadata object containing all columns, primary keys, and column types.
     * @throws SQLException if a database access error occurs.
     */
    public static TableMetadata getTableMetadata(Connection conn, String schema, String tableName) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();

        List<String> allColumns = new ArrayList<>();
        Map<String, String> columnTypes = new HashMap<>();
        Map<String, ColumnMetadata> columnsByName = new HashMap<>();
        try (ResultSet rs = meta.getColumns(null, schema, tableName, null)) {
            while (rs.next()) {
                String colName = rs.getString("COLUMN_NAME");
                String typeName = rs.getString("TYPE_NAME");
                String defaultValue = readOptionalString(rs, "COLUMN_DEF");
                boolean generatedColumn = "YES".equalsIgnoreCase(readOptionalString(rs, "IS_GENERATEDCOLUMN"));
                boolean identityColumn = "YES".equalsIgnoreCase(readOptionalString(rs, "IS_AUTOINCREMENT"));
                allColumns.add(colName);
                columnTypes.put(colName, typeName);
                columnsByName.put(colName, new ColumnMetadata(
                        colName,
                        typeName,
                        defaultValue,
                        generatedColumn,
                        identityColumn));
            }
        }

        mergeInformationSchemaColumnDetails(conn, schema, tableName, columnsByName);

        List<String> primaryKeys = new ArrayList<>();
        try (ResultSet rs = meta.getPrimaryKeys(null, schema, tableName)) {
            while (rs.next()) {
                primaryKeys.add(rs.getString("COLUMN_NAME"));
            }
        }

        return new TableMetadata(allColumns, primaryKeys, columnTypes, columnsByName);
    }

    /**
     * Retrieves a list of all table names for a given schema.
     *
     * @param conn   An open JDBC SimpleConnection.
     * @param schema The schema name (use null for default schema).
     * @return A list of table names.
     * @throws SQLException if a database access error occurs.
     */
    public static List<String> getAllTables(Connection conn, String schema) throws SQLException {
        List<String> tables = new ArrayList<>();
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getTables(null, schema, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }
        }
        return tables;
    }

    /**
     * Retrieves a list of foreign key column names for the given table.
     *
     * @param conn      An open JDBC SimpleConnection.
     * @param schema    The schema name (can be null for default).
     * @param tableName The table name.
     * @return A list of foreign key column names.
     * @throws SQLException if a database access error occurs.
     */
    public static List<String> getForeignKeys(Connection conn, String schema, String tableName) throws SQLException {
        List<String> fks = new ArrayList<>();
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getImportedKeys(null, schema, tableName)) {
            while (rs.next()) {
                String fkColumn = rs.getString("FKCOLUMN_NAME");
                if (!fks.contains(fkColumn)) {
                    fks.add(fkColumn);
                }
            }
        }
        return fks;
    }

    private static Map<String, ColumnMetadata> defaultColumnMetadata(List<String> columns, Map<String, String> columnTypes) {
        Map<String, ColumnMetadata> metadata = new HashMap<>();
        for (String column : columns) {
            metadata.put(column, new ColumnMetadata(column, columnTypes.get(column), null, false, false));
        }
        return metadata;
    }

    private static String readOptionalString(ResultSet rs, String columnName) {
        try {
            return rs.getString(columnName);
        } catch (SQLException ignored) {
            return null;
        }
    }

    private static boolean isSequenceDefault(String defaultValue) {
        return defaultValue != null
                && defaultValue.toLowerCase(Locale.ROOT).contains("nextval(");
    }

    private static void mergeInformationSchemaColumnDetails(Connection conn,
                                                           String schema,
                                                           String tableName,
                                                           Map<String, ColumnMetadata> columnsByName) {
        String sql = """
                SELECT column_name, column_default, is_generated, is_identity
                FROM information_schema.columns
                WHERE table_name = ?
                  AND (? IS NULL OR table_schema = ?)
                """;

        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, tableName);
            statement.setString(2, schema);
            statement.setString(3, schema);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String columnName = rs.getString("column_name");
                    ColumnMetadata existing = columnsByName.get(columnName);
                    if (existing == null) {
                        continue;
                    }

                    String defaultValue = rs.getString("column_default");
                    boolean generatedColumn = "ALWAYS".equalsIgnoreCase(rs.getString("is_generated"));
                    boolean identityColumn = "YES".equalsIgnoreCase(rs.getString("is_identity"));
                    columnsByName.put(columnName, new ColumnMetadata(
                            columnName,
                            existing.getTypeName(),
                            defaultValue,
                            generatedColumn,
                            identityColumn));
                }
            }
        } catch (SQLException ignored) {
            // JDBC metadata above remains the portable fallback.
        }
    }
}
