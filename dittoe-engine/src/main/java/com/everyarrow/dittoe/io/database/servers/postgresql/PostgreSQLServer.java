package com.everyarrow.dittoe.io.database.servers.postgresql;

import org.checkerframework.checker.nullness.qual.NonNull;
import com.everyarrow.dittoe.io.OrchestrationMetadataProvider;
import com.everyarrow.dittoe.io.database.servers.jdbc.JDBCServer;
import com.everyarrow.dittoe.io.database.servers.postgresql.reader.PostgreSQLReader;
import com.everyarrow.dittoe.io.database.servers.postgresql.writer.PostgreSQLWriter;
import com.everyarrow.dittoe.io.database.servers.postgresql.writer.strategy.UpsertOnConflictStrategy;
import com.everyarrow.dittoe.io.database.servers.mssql.writer.strategy.MergeStrategy;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.DataType;
import org.everyarrow.dittoe.schema.Field;
import org.everyarrow.dittoe.schema.Resource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class PostgreSQLServer extends JDBCServer implements OrchestrationMetadataProvider {
    /**
     * Constant for property key "schema".
     */
    protected static final String SCHEMA = "schema";

    public PostgreSQLServer(Boolean isSource) throws ExecutionException {
        super(isSource ? new PostgreSQLReader() : null, isSource ? null : new PostgreSQLWriter());

        if (isSource)
            this.getReader().setServer(this);
        else
            this.getWriter().setServer(this);
    }

    @Override
    public void init() throws ExecutionException {
        super.init();
    }

    @Override
    public String getSafeResourceName() {
        String schema = this.getResource().getResourceProperties().get(SCHEMA);
        return "%s.%s".formatted(this.escapeIdentifierName(schema),
                this.escapeIdentifierName(this.getResource().getResourceName()));
    }

    @Override
    public boolean supportsTargetTableAutoCreate() {
        return true;
    }

    @Override
    public void createTargetTableIfNotExists(Connection connection, List<Field> fields) throws SQLException {
        String schema = this.getResource().getResourceProperties().get(SCHEMA);
        if (schema != null && !schema.isBlank()) {
            String createSchemaQuery = new StringBuilder("create schema if not exists ")
                    .append(this.escapeIdentifierName(schema))
                    .toString();
            try (PreparedStatement statement = connection.prepareStatement(createSchemaQuery)) {
                statement.execute();
            }
        }

        StringBuilder query = new StringBuilder("create table if not exists ")
                .append(this.getSafeResourceName())
                .append(" (")
                .append(this.buildCreateTableColumnDefinitions(fields));

        String mergeMatchColumns = this.getResource().getResourceProperties().get(MergeStrategy.MERGE_MATCH_COLUMNS);
        if (mergeMatchColumns != null && !mergeMatchColumns.isBlank()) {
            String constraintName = this.getResource().getResourceProperties()
                    .getOrDefault(UpsertOnConflictStrategy.UPSERT_CONFLICT_CONSTRAINT,
                            this.getResource().getResourceName() + "_pkey");
            String primaryKeyColumns = Arrays.stream(mergeMatchColumns.split(","))
                    .map(String::trim)
                    .filter(column -> !column.isBlank())
                    .map(this::escapeIdentifierName)
                    .collect(Collectors.joining(", "));
            if (!primaryKeyColumns.isBlank()) {
                query.append(", constraint ")
                        .append(this.escapeIdentifierName(constraintName))
                        .append(" primary key (")
                        .append(primaryKeyColumns)
                        .append(")");
            }
        }

        query.append(")");
        try (PreparedStatement statement = connection.prepareStatement(query.toString())) {
            statement.execute();
        }
    }

    @Override
    public String escapeIdentifierName(String identifierName) {
        return "\""
                .concat(identifierName
                        .replaceAll("\"", "\"\""))
                .concat("\"");
    }

    @Override
    protected void validateConnectionProperties() throws ExecutionException {
        this.validateConnectionStringProperties();
        this.validateConnectionCredentialProperties();
    }

    @Override
    protected Properties getCredentialProperties() {
        Map<String, String> propertiesMap = this.getConnectionDetails();
        Properties properties = new Properties();
        String[] keys = new String[]{USER, PASSWORD};

        for (String key : keys) {
            properties.setProperty(key, propertiesMap.get(key));
        }

        return properties;
    }

    @Override
    protected String getConnectionString() {
        Map<String, String> properties = this.getConnectionDetails();

        return "jdbc:postgresql://%s:%s/%s".formatted(properties.get(HOST), properties.get(PORT),
                properties.get(DATABASE));
    }

    @Override
    protected String getDriverClassName() {
        return "org.postgresql.Driver";
    }

    @Override
    public String getNativeDataType(DataType dataType) {
        return switch (dataType) {
            case DataType.UUID -> "uuid";
            case DataType.Date -> "date";
            case DataType.JSON -> "json";
            case DataType.JSONB -> "jsonb";
            case DataType.DateTime -> "timestamp";
            case DataType.Integer -> "int";
            case DataType.DateTimeWithTimeZone -> "timestamptz";
            case DataType.Decimal -> "numeric";
            case DataType.Double -> "double precision";
            case DataType.Boolean -> "bool";
            case DataType.Time -> "time";
            case DataType.TimeWithTimeZone -> "timetz";
            case DataType.String -> "varchar";
            case DataType.Array -> "text[]"; // Adjust based on specific array type
            case DataType.Number -> "numeric"; // Use numeric for arbitrary precision
            case DataType.Bytes -> "bytea";
            case DataType.XML -> "xml";
        };
    }

    @Override
    public DataType getDataType(@NonNull String columnTypeName) throws ExecutionException {
        if (columnTypeName.startsWith("_"))
            return DataType.JSON;

        DataType dataType = switch (columnTypeName) {
            case "int2", "serial", "bigserial", "smallserial",
                 "int4", "old", "int8", "integer", "smallint", "bigint" -> DataType.Integer;
            case "money", "numeric", "float4", "float8", "real", "double precision" -> DataType.Decimal;
            case "char", "bpchar", "varchar", "text",
                 "name", "point", "box", "character varying", "character" -> DataType.String;
            case "bit", "bool", "boolean" -> DataType.Boolean;
            case "date" -> DataType.Date;
            case "time", "time without time zone" -> DataType.Time;
            case "timetz", "time with time zone" -> DataType.TimeWithTimeZone;
            case "timestamp", "timestamp without time zone" -> DataType.DateTime;
            case "timestamptz", "timestamp with time zone" -> DataType.DateTimeWithTimeZone;
            case "json" -> DataType.JSON;
            case "jsonb" -> DataType.JSONB;
            case "uuid" -> DataType.UUID;
            case "bytea" -> DataType.Bytes;
            case "xml" -> DataType.XML;
            default -> null;
        };

        if (dataType == null) {
            this.getLogger().info("Default data type is being used because the field data type could not be matched. " +
                    "Field data type: " + columnTypeName);
            dataType = DataType.String;
        }

        return dataType;
    }

    private void validateConnectionStringProperties() throws ExecutionException {
        String[] keys = new String[]{HOST, PORT, DATABASE};
        Map<String, String> connectionDetails = this.getConnectionDetails();

        for (String key : keys) {
            if (!connectionDetails.containsKey(key))
                throw new ExecutionException("%s property must be provided in server".formatted(key));
        }

        if (this.getResource() != null
                && !this.getResource().getResourceProperties().containsKey(SCHEMA))
            throw new ExecutionException("%s property must be provided in resource".formatted(SCHEMA));
    }

    private void validateConnectionCredentialProperties() throws ExecutionException {
        String[] keys = new String[]{USER, PASSWORD};
        Map<String, String> properties = this.getConnectionDetails();

        for (String key : keys) {
            if (!properties.containsKey(key))
                throw new ExecutionException("%s property must be provided in server".formatted(key));
        }
    }

    @Override
    protected boolean isSystemSchema(String schemaName) {
        return schemaName.equalsIgnoreCase("pg_catalog") ||
                schemaName.equalsIgnoreCase("information_schema") ||
                schemaName.startsWith("pg_toast") ||  // Matches all toast schemas
                schemaName.startsWith("pg_temp") ||  // Matches all temp schemas
                schemaName.equalsIgnoreCase("pg_statistic");
    }

    @Override
    public String getJsonBuilderQuery() {
        return "row_to_json(t)";
    }

    /**
     * Override the getResources method to use PostgreSQL-specific query for better performance
     * and more accurate schema filtering using information_schema.
     */
    @Override
    public List<Resource> getResources() {
        try (Connection connection = this.getConnection()) {
            Map<String, Resource> resources = new HashMap<>();

            String query = """
                    WITH primary_keys AS (
                        SELECT
                            table_namespace.nspname AS table_schema,
                            table_row.relname AS table_name,
                            string_agg(attribute_row.attname, ',' ORDER BY key_column.ordinality) AS columns
                        FROM pg_constraint constraint_row
                        JOIN pg_class table_row ON table_row.oid = constraint_row.conrelid
                        JOIN pg_namespace table_namespace ON table_namespace.oid = table_row.relnamespace
                        JOIN LATERAL unnest(constraint_row.conkey) WITH ORDINALITY
                          AS key_column(attnum, ordinality) ON true
                        JOIN pg_attribute attribute_row
                          ON attribute_row.attrelid = table_row.oid
                         AND attribute_row.attnum = key_column.attnum
                        WHERE constraint_row.contype = 'p'
                        GROUP BY table_namespace.nspname, table_row.relname
                    ),
                    foreign_key_constraints AS (
                        SELECT
                            constraint_row.oid,
                            source_namespace.nspname AS table_schema,
                            source_table.relname AS table_name,
                            target_namespace.nspname AS referenced_schema,
                            target_table.relname AS referenced_table,
                            array_agg(source_attribute.attname ORDER BY key_pair.ordinality) AS source_columns,
                            array_agg(target_attribute.attname ORDER BY key_pair.ordinality) AS referenced_columns
                        FROM pg_constraint constraint_row
                        JOIN pg_class source_table ON source_table.oid = constraint_row.conrelid
                        JOIN pg_namespace source_namespace ON source_namespace.oid = source_table.relnamespace
                        JOIN pg_class target_table ON target_table.oid = constraint_row.confrelid
                        JOIN pg_namespace target_namespace ON target_namespace.oid = target_table.relnamespace
                        JOIN LATERAL unnest(constraint_row.conkey, constraint_row.confkey) WITH ORDINALITY
                          AS key_pair(source_attnum, target_attnum, ordinality) ON true
                        JOIN pg_attribute source_attribute
                          ON source_attribute.attrelid = source_table.oid
                         AND source_attribute.attnum = key_pair.source_attnum
                        JOIN pg_attribute target_attribute
                          ON target_attribute.attrelid = target_table.oid
                         AND target_attribute.attnum = key_pair.target_attnum
                        WHERE constraint_row.contype = 'f'
                        GROUP BY
                            constraint_row.oid,
                            source_namespace.nspname,
                            source_table.relname,
                            target_namespace.nspname,
                            target_table.relname
                    ),
                    foreign_keys AS (
                        SELECT
                            table_schema,
                            table_name,
                            string_agg(array_to_string(source_columns, ','), ',' ORDER BY oid) AS columns,
                            jsonb_agg(
                                jsonb_build_object(
                                    'columns', to_jsonb(source_columns),
                                    'references', jsonb_build_object(
                                        'schema', referenced_schema,
                                        'table', referenced_table,
                                        'columns', to_jsonb(referenced_columns)
                                    )
                                ) ORDER BY oid
                            )::text AS relationships
                        FROM foreign_key_constraints
                        GROUP BY table_schema, table_name
                    )
                    SELECT
                        c.table_schema AS schema_name,
                        c.table_name,
                        c.column_name,
                        c.data_type,
                        c.is_nullable,
                        t.table_type,
                        COALESCE(pk.columns, '') AS primary_keys,
                        COALESCE(fk.columns, '') AS foreign_keys,
                        COALESCE(fk.relationships, '[]') AS relationships
                    FROM information_schema.columns c
                    JOIN information_schema.tables t
                      ON t.table_schema = c.table_schema
                     AND t.table_name = c.table_name
                    LEFT JOIN primary_keys pk
                      ON pk.table_schema = c.table_schema
                     AND pk.table_name = c.table_name
                    LEFT JOIN foreign_keys fk
                      ON fk.table_schema = c.table_schema
                     AND fk.table_name = c.table_name
                    WHERE c.table_schema NOT IN ('pg_catalog', 'information_schema')
                    ORDER BY
                        c.table_schema,
                        c.table_name,
                        c.ordinal_position
                    """;

            try (PreparedStatement stmt = connection.prepareStatement(query);
                 ResultSet resultSet = stmt.executeQuery()) {

                AtomicInteger counter = new AtomicInteger(1);

                while (resultSet.next()) {
                    String tableSchema = resultSet.getString("schema_name");
                    String tableName = resultSet.getString("table_name");
                    String columnName = resultSet.getString("column_name");
                    String dataType = resultSet.getString("data_type");
                    boolean isNullable = resultSet.getString("is_nullable").equalsIgnoreCase("YES");

                    // Create or update resource for the table
                    String resourceKey = tableSchema + tableName;
                    Resource resource = resources.computeIfAbsent(resourceKey, k -> {
                        Resource newResource = new Resource();
                        newResource.setResourceName(tableName);
                        newResource.setServer(PostgreSQLServer.this);
                        newResource.setResourceID(counter.getAndIncrement());
                        newResource.getResourceProperties().put("schema", tableSchema);
                        newResource.getResourceProperties().put(
                                "primaryKeys", readMetadataString(resultSet, "primary_keys"));
                        newResource.getResourceProperties().put(
                                "foreignKeys", readMetadataString(resultSet, "foreign_keys"));
                        newResource.getResourceProperties().put(
                                "relationships", readMetadataString(resultSet, "relationships"));
                        newResource.getResourceProperties().put(
                                "resourceType", readMetadataString(resultSet, "table_type"));
                        newResource.getResourceProperties().put("constraintMetadata", "jdbc");
                        return newResource;
                    });

                    // Add field (column) information to the resource with actual data type
                    resource.getFields().add(new Field(
                            columnName,
                            getDataType(dataType),
                            isNullable
                    )); // Use actual data type from query
                }
            }

            return resources.values().stream().toList();

        } catch (SQLException | ExecutionException e) {
            throw new RuntimeException("Error occurred while retrieving resources", e);
        }
    }

    private static String readMetadataString(ResultSet resultSet, String column) {
        try {
            return resultSet.getString(column);
        } catch (SQLException exception) {
            throw new RuntimeException("Unable to read PostgreSQL constraint metadata", exception);
        }
    }

    @Override
    public Map<String, List<Field>> getResourcesFields(List<Resource> resources) {
        Map<String, List<Field>> result = new HashMap<>();

        if (resources == null || resources.isEmpty()) {
            return result;
        }

        try (Connection connection = this.getConnection()) {
            // Group resources by schema for efficient batch processing
            Map<String, List<Resource>> resourcesBySchema = new HashMap<>();

            for (Resource resource : resources) {
                String tableName = resource.getResourceName();
                if (tableName == null || tableName.isBlank()) {
                    throw new RuntimeException("Resource name (table) cannot be null or blank");
                }

                String schema = resource.getResourceProperties().get(SCHEMA);
                if (schema == null || schema.isBlank()) {
                    throw new RuntimeException("Schema property is required for resource '" + tableName + "'");
                }

                resourcesBySchema.computeIfAbsent(schema, k -> new ArrayList<>()).add(resource);
            }

            // Process resources grouped by schema
            for (Map.Entry<String, List<Resource>> entry : resourcesBySchema.entrySet()) {
                String schema = entry.getKey();
                List<Resource> schemaResources = entry.getValue();

                // Build a map of table names for quick lookup
                Map<String, Resource> tableNameToResource = new HashMap<>();
                for (Resource resource : schemaResources) {
                    tableNameToResource.put(resource.getResourceName(), resource);
                }

                // Query all columns for all tables in this schema in one query
                String query = """
                        SELECT
                            table_name,
                            column_name,
                            data_type,
                            is_nullable,
                            ordinal_position
                        FROM information_schema.columns
                        WHERE table_schema = ?
                        ORDER BY table_name, ordinal_position
                        """;

                try (PreparedStatement stmt = connection.prepareStatement(query)) {
                    stmt.setString(1, schema);

                    try (ResultSet rs = stmt.executeQuery()) {
                        String currentTableName = null;
                        List<Field> currentFields = null;

                        while (rs.next()) {
                            String tableName = rs.getString("table_name");

                            // Check if this table is in our requested resources
                            if (!tableNameToResource.containsKey(tableName)) {
                                continue;
                            }

                            // If we've moved to a new table, save the previous one
                            if (currentTableName != null && !currentTableName.equals(tableName)) {
                                result.put(currentTableName, currentFields);
                            }

                            // Start a new table
                            if (!tableName.equals(currentTableName)) {
                                currentTableName = tableName;
                                currentFields = new ArrayList<>();
                            }

                            String columnName = rs.getString("column_name");
                            String dataType = rs.getString("data_type");
                            boolean isNullable = rs.getString("is_nullable").equalsIgnoreCase("YES");

                            currentFields.add(new Field(columnName, getDataType(dataType), isNullable));
                        }

                        // Don't forget to add the last table
                        if (currentTableName != null) {
                            result.put(currentTableName, currentFields);
                        }
                    }
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Error occurred while retrieving fields for resources", e);
        }

        return result;
    }

    @Override
    public Map<String, Map<String, String>> getPropertiesForMerge(List<Resource> resources) {
        Map<String, Map<String, String>> result = new HashMap<>();

        try {
            Map<String, PrimaryKeyInfo> allPrimaryKeys = getAllPrimaryKeyInfo(resources);
            for (Resource resource : resources) {
                String resourceName = resource.getResourceName();
                String schema = resource.getResourceProperties().get(SCHEMA);
                PrimaryKeyInfo primaryKeyInfo = allPrimaryKeys.get(resourceKey(schema, resourceName));
                Map<String, String> mergeProperties = new HashMap<>();

                if (primaryKeyInfo != null && !primaryKeyInfo.columns.isEmpty()) {
                    mergeProperties.put(UpsertOnConflictStrategy.UPSERT_CONFLICT_CONSTRAINT, primaryKeyInfo.constraintName);
                    mergeProperties.put(MergeStrategy.MERGE_MATCH_COLUMNS,
                            String.join(",", primaryKeyInfo.columns));
                } else {
                    getLogger().info("No primary key constraints found for table {}, upsert disabled", resourceName);
                }

                result.put(resourceName, mergeProperties);
            }

            getLogger().info("Generated merge properties for {} resources", result.size());
        } catch (Exception e) {
            getLogger().error("Error occurred while generating merge properties: {}", e.getMessage(), e);
        }

        return result;
    }

    /**
     * Retrieves all primary key information (constraint names and columns) for all tables in the database using a single query.
     * This method is optimized to fetch all primary key information in one database call.
     *
     * @return Map where key is "schema.tableName" and value is PrimaryKeyInfo containing constraint name and column list
     */
    private Map<String, PrimaryKeyInfo> getAllPrimaryKeyInfo(List<Resource> resources) {
        Map<String, Map<String, List<String>>> primaryKeysMap = new HashMap<>();
        Map<String, String> constraintNames = new HashMap<>();

        if (resources == null || resources.isEmpty()) {
            return new HashMap<>();
        }

        // Build the IN clause with placeholders
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("""
                SELECT
                    n.nspname AS schema_name,
                    t.relname AS table_name,
                    c.conname AS constraint_name,
                    a.attname AS column_name,
                    a.attnum AS column_position
                FROM
                    pg_constraint c
                    JOIN pg_class t ON c.conrelid = t.oid
                    JOIN pg_namespace n ON t.relnamespace = n.oid
                    JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = ANY(c.conkey)
                WHERE
                    c.contype = 'p'
                    AND n.nspname NOT IN ('information_schema', 'pg_catalog', 'pg_toast')
                    AND (n.nspname, t.relname) IN (
                """);

        // Match schema and table together. Table names are not unique across PostgreSQL schemas.
        for (int i = 0; i < resources.size(); i++) {
            if (i > 0) queryBuilder.append(", ");
            queryBuilder.append("(?, ?)");
        }

        queryBuilder.append("""
                )
                ORDER BY
                    n.nspname, t.relname, a.attnum
                """);

        String query = queryBuilder.toString();

        try (Connection connection = this.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {

            // Set each schema/table pair as parameters.
            for (int i = 0; i < resources.size(); i++) {
                Resource resource = resources.get(i);
                statement.setString((i * 2) + 1, resource.getResourceProperties().get(SCHEMA));
                statement.setString((i * 2) + 2, resource.getResourceName());
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String schemaName = resultSet.getString("schema_name");
                    String tableName = resultSet.getString("table_name");
                    String constraintName = resultSet.getString("constraint_name");
                    String columnName = resultSet.getString("column_name");

                    String resourceKey = resourceKey(schemaName, tableName);
                    constraintNames.put(resourceKey, constraintName);

                    primaryKeysMap.computeIfAbsent(resourceKey, k -> new HashMap<>())
                            .computeIfAbsent(constraintName, k -> new ArrayList<>())
                            .add(columnName);
                }
            }

        } catch (SQLException | ExecutionException e) {
            getLogger().error("Error executing primary key query: {}", e.getMessage(), e);
        }

        Map<String, PrimaryKeyInfo> result = new HashMap<>();
        for (Map.Entry<String, Map<String, List<String>>> entry : primaryKeysMap.entrySet()) {
            String resourceKey = entry.getKey();
            String constraintName = constraintNames.get(resourceKey);

            List<String> columns = entry.getValue().values().iterator().next();
            result.put(resourceKey, new PrimaryKeyInfo(constraintName, columns));
        }

        return result;
    }

    private static String resourceKey(String schemaName, String tableName) {
        return schemaName + "." + tableName;
    }

    /**
     * Inner class to hold primary key information including constraint name and columns.
     */
    private record PrimaryKeyInfo(String constraintName, List<String> columns) {
    }
}
