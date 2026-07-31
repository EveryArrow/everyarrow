package com.everyarrow.dittoe.io.database.servers.mssql;

import org.checkerframework.checker.nullness.qual.NonNull;
import com.everyarrow.dittoe.io.OrchestrationMetadataProvider;
import com.everyarrow.dittoe.io.database.servers.jdbc.JDBCServer;
import com.everyarrow.dittoe.io.database.servers.mssql.reader.MSSQLReader;
import com.everyarrow.dittoe.io.database.servers.mssql.writer.MSSQLWriter;
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

/**
 * MSSQLServer is a JDBC server implementation for Microsoft SQL Server.
 * <p>
 * This class provides methods to initialize the connection, construct
 * SQL Server–specific connection strings, escape identifiers, validate connection
 * properties, and map generic data types to native SQL Server data types.
 * </p>
 *
 * <p><strong>Usage:</strong></p>
 * <pre>
 *   // For reading from a SQL Server instance:
 *   MSSQLServer mssqlSource = new MSSQLServer(true);
 *
 *   // For writing to a SQL Server instance:
 *   MSSQLServer mssqlTarget = new MSSQLServer(false);
 * </pre>
 *
 * <p>The following connection properties are expected to be provided in the server configuration:
 * <ul>
 *     <li>{@code HOST} - the SQL Server host name or IP address</li>
 *     <li>{@code PORT} - the port number</li>
 *     <li>{@code DATABASE} - the target database name</li>
 *     <li>{@code USER} and {@code PASSWORD} - credentials for authentication</li>
 *     <li>{@code encrypted} - (optional) set to "true" to use an encrypted connection</li>
 *     <li>{@code trustServerCertificate} - (optional) set to "true" if the server certificate should be trusted without validation</li>
 * </ul>
 * Additionally, the resource properties should include the key {@code schema} specifying the SQL Server schema.
 * </p>
 *
 * @see JDBCServer
 * @see DataType
 */
public class MSSQLServer extends JDBCServer implements OrchestrationMetadataProvider {

    /**
     * Constant for the property key "schema".
     */
    protected static final String SCHEMA = "schema";

    /**
     * Constant for the property key "encrypted".
     */
    protected static final String ENCRYPTED = "encrypted";

    /**
     * Constant for the property key "trustServerCertificate".
     */
    protected static final String TRUST_SERVER_CERTIFICATE = "trustServerCertificate";

    /**
     * Constructs a new MSSQLServer.
     *
     * @param isSource true if this server instance is used as a source, false if used as a target.
     * @throws ExecutionException if an error occurs during initialization.
     */
    public MSSQLServer(Boolean isSource) throws ExecutionException {
        super(isSource ? new MSSQLReader() : null, isSource ? null : new MSSQLWriter());

        // Set the server reference in the corresponding reader/writer.
        if (isSource) this.getReader().setServer(this);
        else this.getWriter().setServer(this);
    }

    @Override
    public void init() throws ExecutionException {
        super.init();
    }

    /**
     * Returns the fully qualified and escaped resource name in the format [schema].[table].
     *
     * @return the safe resource name.
     */
    @Override
    public String getSafeResourceName() {
        String schema = this.getResource().getResourceProperties().get(SCHEMA);
        return "%s.%s".formatted(this.escapeIdentifierName(schema), this.escapeIdentifierName(this.getResource().getResourceName()));
    }

    @Override
    public boolean supportsTargetTableAutoCreate() {
        return true;
    }

    @Override
    public void createTargetTableIfNotExists(Connection connection, List<Field> fields) throws SQLException {
        String schema = this.getResource().getResourceProperties().get(SCHEMA);
        if (schema != null && !schema.isBlank()) {
            String createSchemaQuery = new StringBuilder(
                    "if schema_id(N'")
                    .append(schema.replace("'", "''"))
                    .append("') is null exec('create schema ")
                    .append(this.escapeIdentifierName(schema))
                    .append("')")
                    .toString();
            try (PreparedStatement statement = connection.prepareStatement(createSchemaQuery)) {
                statement.execute();
            }
        }

        StringBuilder query = new StringBuilder("if object_id(N'")
                .append(this.getSafeResourceName().replace("'", "''"))
                .append("', N'U') is null create table ")
                .append(this.getSafeResourceName())
                .append(" (")
                .append(this.buildCreateTableColumnDefinitions(fields));

        String mergeMatchColumns = this.getResource().getResourceProperties().get(MergeStrategy.MERGE_MATCH_COLUMNS);
        if (mergeMatchColumns != null && !mergeMatchColumns.isBlank()) {
            String primaryKeyColumns = Arrays.stream(mergeMatchColumns.split(","))
                    .map(String::trim)
                    .filter(column -> !column.isBlank())
                    .map(this::escapeIdentifierName)
                    .collect(Collectors.joining(", "));
            if (!primaryKeyColumns.isBlank()) {
                query.append(", primary key (")
                        .append(primaryKeyColumns)
                        .append(")");
            }
        }

        query.append(")");
        try (PreparedStatement statement = connection.prepareStatement(query.toString())) {
            statement.execute();
        }
    }

    /**
     * Escapes an identifier name using SQL Server's identifier escaping mechanism (square brackets).
     *
     * @param identifierName the identifier name to escape.
     * @return the escaped identifier name.
     */
    @Override
    public String escapeIdentifierName(String identifierName) {
        // In SQL Server, any closing bracket in the name should be escaped by doubling it.
        return "[" + identifierName.replace("]", "]]") + "]";
    }

    @Override
    protected void validateConnectionProperties() throws ExecutionException {
        validateConnectionStringProperties();
        validateConnectionCredentialProperties();
    }

    /**
     * Retrieves the credential properties required for connection.
     *
     * @return the Properties object containing the credentials.
     */
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

    /**
     * Constructs the SQL Server JDBC connection string.
     * <p>
     * Expected connection details include: HOST, PORT, and DATABASE.
     * Additionally, if provided, the {@code encrypted} and {@code trustServerCertificate} properties
     * will be appended to the connection string to enable encryption and certificate trust options.
     * </p>
     *
     * @return the SQL Server connection string.
     */
    @Override
    protected String getConnectionString() {
        Map<String, String> properties = this.getConnectionDetails();
        // SQL Server connection string format:
        // jdbc:sqlserver://<host>:<port>;databaseName=<database>[;encrypt=<value>][;trustServerCertificate=<value>]
        StringBuilder sb = new StringBuilder("jdbc:sqlserver://%s:%s;databaseName=%s".formatted(properties.get(HOST), properties.get(PORT), properties.get(DATABASE)));

        // Append encryption property if provided.
        if (properties.containsKey(ENCRYPTED)) {
            sb.append(";encrypt=").append(properties.get(ENCRYPTED));
        }
        // Append trustServerCertificate property if provided.
        if (properties.containsKey(TRUST_SERVER_CERTIFICATE)) {
            sb.append(";trustServerCertificate=").append(properties.get(TRUST_SERVER_CERTIFICATE));
        }
        return sb.toString();
    }

    /**
     * Returns the fully qualified driver class name for SQL Server.
     *
     * @return the SQL Server driver class name.
     */
    @Override
    protected String getDriverClassName() {
        return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
    }

    /**
     * Maps a generic DataType to a native SQL Server data type.
     *
     * @param dataType the generic data type.
     * @return the corresponding native SQL Server data type as a String.
     */
    @Override
    public String getNativeDataType(DataType dataType) {
        return switch (dataType) {
            case UUID -> "uniqueidentifier";
            case Date -> "date";
            case JSON, JSONB -> "nvarchar(max)"; // SQL Server does not have a native JSON type; use nvarchar(max)
            case DateTime -> "datetime2";
            case Integer -> "int";
            case DateTimeWithTimeZone -> "datetimeoffset";
            case Decimal -> "numeric";
            case Double -> "float";
            case Boolean -> "bit";
            case Time -> "time";
            case TimeWithTimeZone -> "time"; // No native type for time with time zone; default to time
            case String -> "nvarchar(255)"; // Default length; adjust if needed
            case Array ->
                    "nvarchar(max)";   // Adjust based on specific array type; consider storing as JSON if appropriate
            case Number -> "numeric";        // Use numeric for arbitrary precision numbers
            case Bytes -> "varbinary";
            case XML -> "xml";
        };
    }

    /**
     * Determines the generic DataType for a given native SQL Server column type.
     *
     * @param columnTypeName the native column type name.
     * @return the corresponding DataType.
     * @throws ExecutionException if the column type cannot be mapped.
     */
    @Override
    public DataType getDataType(@NonNull String columnTypeName) throws ExecutionException {
        DataType dataType = switch (columnTypeName.toLowerCase()) {
            case "int", "bigint", "smallint", "tinyint" -> DataType.Integer;
            case "decimal", "numeric", "money", "smallmoney" -> DataType.Decimal;
            case "float", "real" -> DataType.Double;
            case "bit" -> DataType.Boolean;
            case "char", "nchar", "varchar", "nvarchar", "text", "ntext" -> DataType.String;
            case "date" -> DataType.Date;
            case "datetime", "datetime2", "smalldatetime" -> DataType.DateTime;
            case "datetimeoffset" -> DataType.DateTimeWithTimeZone;
            case "time" -> DataType.Time;
            case "uniqueidentifier" -> DataType.UUID;
            case "varbinary" -> DataType.Bytes;
            case "xml" -> DataType.XML;
            default -> null;
        };

        if (dataType == null) {
            this.getLogger().info("Default data type is being used because the field data type could not be matched. " + "Field data type: " + columnTypeName);
            dataType = DataType.String;
        }

        return dataType;
    }

    /**
     * Validates that all required connection string properties are present.
     *
     * @throws ExecutionException if any required property is missing.
     */
    private void validateConnectionStringProperties() throws ExecutionException {
        String[] keys = new String[]{HOST, PORT, DATABASE};
        Map<String, String> connectionDetails = this.getConnectionDetails();

        for (String key : keys) {
            if (!connectionDetails.containsKey(key)) {
                throw new ExecutionException("%s property must be provided in server".formatted(key));
            }
        }

        if (!this.getResource().getResourceProperties().containsKey(SCHEMA)) {
            throw new ExecutionException("%s property must be provided in resource".formatted(SCHEMA));
        }
    }

    /**
     * Validates that the required connection credential properties are provided.
     *
     * @throws ExecutionException if any credential property is missing.
     */
    private void validateConnectionCredentialProperties() throws ExecutionException {
        String[] keys = new String[]{USER, PASSWORD};
        Map<String, String> properties = this.getConnectionDetails();

        for (String key : keys) {
            if (!properties.containsKey(key)) {
                throw new ExecutionException("%s property must be provided in server".formatted(key));
            }
        }
    }

    /**
     * Determines if a given schema is a system schema in SQL Server.
     * <p>
     * System schemas typically include "sys" and "INFORMATION_SCHEMA".
     * </p>
     *
     * @param schemaName the name of the schema.
     * @return true if the schema is a system schema; false otherwise.
     */
    @Override
    protected boolean isSystemSchema(String schemaName) {
        return schemaName.equalsIgnoreCase("sys") || schemaName.equalsIgnoreCase("INFORMATION_SCHEMA");
    }

    /**
     * Returns the SQL Server JSON builder query fragment.
     * <p>
     * In SQL Server, JSON output is generated using the <code>FOR JSON PATH</code>
     * clause. The option <code>WITHOUT_ARRAY_WRAPPER</code> can be used to return a single JSON object.
     * </p>
     *
     * @return the JSON builder query fragment.
     */
    @Override
    public String getJsonBuilderQuery() {
        return "FOR JSON PATH, WITHOUT_ARRAY_WRAPPER";
    }

    /**
     * Override the getResources method to use MSSQL-specific query for better performance
     * and more accurate schema filtering.
     */
    @Override
    public List<Resource> getResources() {
        try (Connection connection = this.getConnection()) {
            Map<String, Resource> resources = new HashMap<>();

            String query = """
                        SELECT
                            s.name AS schema_name,
                            t.name AS table_name,
                            c.name AS column_name,
                            coalesce(ty.name, 'nvarchar') AS data_type,
                            c.is_nullable
                        FROM sys.schemas s
                        JOIN sys.tables t
                            ON s.schema_id = t.schema_id
                        JOIN sys.columns c
                            ON t.object_id = c.object_id
                        LEFT JOIN sys.types ty
                            ON c.user_type_id = ty.user_type_id
                        WHERE s.name NOT IN ('sys', 'INFORMATION_SCHEMA')  -- exclude system schemas
                        ORDER BY
                            s.name,
                            t.name,
                            c.column_id
                    """;

            try (PreparedStatement stmt = connection.prepareStatement(query);
                 ResultSet resultSet = stmt.executeQuery()) {

                AtomicInteger counter = new AtomicInteger(1);

                while (resultSet.next()) {
                    String tableSchema = resultSet.getString("schema_name");
                    String tableName = resultSet.getString("table_name");
                    String columnName = resultSet.getString("column_name");
                    String dataType = resultSet.getString("data_type");
                    boolean isNullable = resultSet.getInt("is_nullable") == 1;

                    // Create or update resource for the table
                    String resourceKey = tableSchema + tableName;
                    Resource resource = resources.computeIfAbsent(resourceKey, k -> {
                        Resource newResource = new Resource();
                        newResource.setResourceName(tableName);
                        newResource.setServer(MSSQLServer.this);
                        newResource.setResourceID(counter.getAndIncrement());
                        newResource.getResourceProperties().put("schema", tableSchema);
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
                            t.name AS table_name,
                            c.name AS column_name,
                            COALESCE(ty.name, 'nvarchar') AS data_type,
                            c.is_nullable,
                            c.column_id
                        FROM sys.schemas s
                        JOIN sys.tables t
                            ON s.schema_id = t.schema_id
                        JOIN sys.columns c
                            ON t.object_id = c.object_id
                        LEFT JOIN sys.types ty
                            ON c.user_type_id = ty.user_type_id
                        WHERE s.name = ?
                        ORDER BY t.name, c.column_id
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
                            boolean isNullable = rs.getInt("is_nullable") == 1;

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
                PrimaryKeyInfo primaryKeyInfo = allPrimaryKeys.get(resourceName);
                Map<String, String> mergeProperties = new HashMap<>();

                if (primaryKeyInfo != null && !primaryKeyInfo.columns.isEmpty()) {
                    String primaryKeyColumnsStr = String.join(",", primaryKeyInfo.columns);
                    mergeProperties.put(MergeStrategy.MERGE_MATCH_COLUMNS, primaryKeyColumnsStr);
                } else {
                    getLogger().info("No primary key columns found for table {}, merge disabled", resourceName);
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
     * This method is optimized to fetch all primary key information in one database call for MSSQL.
     *
     * @return Map where key is "[schema].[tableName]" and value is PrimaryKeyInfo containing constraint name and column list
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
                    s.name AS schema_name,
                    t.name AS table_name,
                    kc.name AS constraint_name,
                    c.name AS column_name,
                    ic.key_ordinal AS column_position
                FROM sys.schemas s
                JOIN sys.tables t ON s.schema_id = t.schema_id
                JOIN sys.key_constraints kc ON t.object_id = kc.parent_object_id
                JOIN sys.indexes i ON kc.parent_object_id = i.object_id AND kc.unique_index_id = i.index_id
                JOIN sys.index_columns ic ON i.object_id = ic.object_id AND i.index_id = ic.index_id
                JOIN sys.columns c ON ic.object_id = c.object_id AND ic.column_id = c.column_id
                WHERE kc.type = 'PK'
                    AND s.name NOT IN ('sys', 'INFORMATION_SCHEMA')
                    AND t.name IN (
                """);

        // Add placeholders for each resource
        for (int i = 0; i < resources.size(); i++) {
            if (i > 0) queryBuilder.append(", ");
            queryBuilder.append("?");
        }

        queryBuilder.append("""
                )
                ORDER BY s.name, t.name, ic.key_ordinal
                """);

        String query = queryBuilder.toString();

        try (Connection connection = this.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {

            // Set the resource names as parameters
            for (int i = 0; i < resources.size(); i++) {
                statement.setString(i + 1, resources.get(i).getResourceName());
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String schemaName = resultSet.getString("schema_name");
                    String tableName = resultSet.getString("table_name");
                    String constraintName = resultSet.getString("constraint_name");
                    String columnName = resultSet.getString("column_name");

                    constraintNames.put(tableName, constraintName);

                    primaryKeysMap.computeIfAbsent(tableName, k -> new HashMap<>())
                            .computeIfAbsent(constraintName, k -> new ArrayList<>())
                            .add(columnName);
                }
            }

        } catch (SQLException | ExecutionException e) {
            getLogger().error("Error executing primary key query: {}", e.getMessage(), e);
        }

        Map<String, PrimaryKeyInfo> result = new HashMap<>();
        for (Map.Entry<String, Map<String, List<String>>> entry : primaryKeysMap.entrySet()) {
            String tableName = entry.getKey();
            String constraintName = constraintNames.get(tableName);

            List<String> columns = entry.getValue().values().iterator().next();
            result.put(tableName, new PrimaryKeyInfo(constraintName, columns));
        }

        return result;
    }

    /**
     * Inner class to hold primary key information including constraint name and columns.
     */
    private record PrimaryKeyInfo(String constraintName, List<String> columns) {
    }
}
