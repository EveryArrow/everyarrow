package com.everyarrow.dittoe.io.database.servers.mysql;

import com.everyarrow.dittoe.io.OrchestrationMetadataProvider;
import org.everyarrow.dittoe.schema.Field;
import com.everyarrow.dittoe.io.database.servers.jdbc.JDBCServer;
import com.everyarrow.dittoe.io.database.servers.mssql.writer.strategy.MergeStrategy;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.DataType;
import org.everyarrow.dittoe.schema.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;


public class MySQLServer extends JDBCServer implements OrchestrationMetadataProvider {
    private static final String SCHEMA = "schema";
    private static final Set<String> SYSTEM_SCHEMAS = Set.of("information_schema", "mysql", "performance_schema", "sys");
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public MySQLServer(Boolean isSource) throws ExecutionException {
        super(isSource ? new MySQLReader() : null, isSource ? null : new MySQLWriter());

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
        return this.escapeIdentifierName(this.getResource().getResourceName());
    }

    @Override
    public boolean supportsTargetTableAutoCreate() {
        return true;
    }

    @Override
    public void createTargetTableIfNotExists(Connection connection, List<Field> fields) throws SQLException {
        String query = "create table if not exists "
                + this.getSafeResourceName()
                + " ("
                + this.buildCreateTableColumnDefinitions(fields)
                + ")";

        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.execute();
        }
    }

    public String getNativeDataType(DataType dataType) {
        return switch (dataType) {
            case DataType.UUID -> "text"; // MySQL does not have a native UUID type; store as CHAR(36) or BINARY(16)
            case DataType.Date -> "date"; // MySQL supports DATE directly
            case DataType.JSON -> "json"; // MySQL supports JSON natively
            case DataType.JSONB -> "json"; // MySQL does not distinguish between JSON and JSONB
            case DataType.DateTime -> "datetime"; // MySQL uses DATETIME
            case DataType.Integer -> "integer"; // MySQL INTEGER defaults to SIGNED
            case DataType.DateTimeWithTimeZone -> "datetime"; // MySQL does not store time zones in DATETIME
            case DataType.Decimal -> "decimal"; // DECIMAL is supported
            case DataType.Double -> "double"; // DOUBLE is supported
            case DataType.Boolean -> "boolean"; // BOOLEAN is an alias for TINYINT(1) in MySQL
            case DataType.Time -> "time"; // MySQL supports TIME
            case DataType.TimeWithTimeZone -> "time"; // MySQL TIME does not store time zones
            case DataType.String, DataType.Bytes, DataType.XML -> "text"; // MySQL supports VARCHAR, TEXT, CHAR
            case DataType.Array ->
                    "text"; // MySQL does not have native array support; JSON or normalized tables should be used
            case DataType.Number -> "decimal"; // DECIMAL for arbitrary precision numbers
        };
    }

    @Override
    public String getDataTypeCast(String fieldName, DataType dataType) {
        return switch (dataType) {
            case DataType.Integer -> "cast(%s as signed)".formatted(fieldName);
            case DataType.Decimal, DataType.Number -> "cast(%s as decimal(65,30))".formatted(fieldName);
            case DataType.Double -> "cast(%s as double)".formatted(fieldName);
            case DataType.Date -> "cast(%s as date)".formatted(fieldName);
            case DataType.DateTime, DataType.DateTimeWithTimeZone -> "cast(%s as datetime)".formatted(fieldName);
            case DataType.Time, DataType.TimeWithTimeZone -> "cast(%s as time)".formatted(fieldName);
            case DataType.Boolean -> fieldName;
            case DataType.UUID, DataType.String, DataType.Bytes, DataType.XML, DataType.Array, DataType.JSON, DataType.JSONB -> "cast(%s as char)".formatted(fieldName);
        };
    }


    @Override
    public DataType getDataType(String columnTypeName) throws ExecutionException {
        if (columnTypeName == null) return DataType.String;
        String t = columnTypeName.toLowerCase();
        return switch (t) {
            case "tinyint", "smallint", "mediumint", "int", "integer", "bigint" -> DataType.Integer;
            case "decimal", "numeric", "fixed" -> DataType.Decimal;
            case "float", "double", "double precision" -> DataType.Double;
            case "bit", "boolean", "bool" -> DataType.Boolean;
            case "date" -> DataType.Date;
            case "datetime", "timestamp" -> DataType.DateTime;
            case "time" -> DataType.Time;
            case "json" -> DataType.JSON;
            case "year" -> DataType.Integer;
            case "char", "varchar", "tinytext", "text", "mediumtext", "longtext", "enum", "set" -> DataType.String;
            case "binary", "varbinary", "tinyblob", "blob", "mediumblob", "longblob" -> DataType.Bytes;
            default -> DataType.String;
        };
    }

    @Override
    public String escapeIdentifierName(String identifierName) {
        return "`"
                .concat(identifierName
                        .replaceAll("`", "``"))
                .concat("`");
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

        return "jdbc:mysql://%s:%s/%s?permitMysqlScheme=true"
                .formatted(properties.get(HOST), properties.get(PORT), properties.get(DATABASE));
    }


    @Override
    protected String getDriverClassName() {
        return "org.mariadb.jdbc.Driver";
    }

    private void validateConnectionStringProperties() throws ExecutionException {
        String[] keys = new String[]{HOST, PORT, DATABASE};
        Map<String, String> connectionDetails = this.getConnectionDetails();

        for (String key : keys) {
            if (!connectionDetails.containsKey(key))
                throw new ExecutionException("%s property must be provided".formatted(key));
        }
    }

    private void validateConnectionCredentialProperties() throws ExecutionException {
        String[] keys = new String[]{USER, PASSWORD};
        Map<String, String> connectionDetails = this.getConnectionDetails();

        for (String key : keys) {
            if (!connectionDetails.containsKey(key))
                throw new ExecutionException("%s property must be provided in server".formatted(key));
        }
    }

    @Override
    protected boolean isSystemSchema(String schemaName) {
        return schemaName != null && SYSTEM_SCHEMAS.contains(schemaName.toLowerCase());
    }

    @Override
    public String getJsonBuilderQuery() {
        return "json_object(" + this.getResource().getFields().stream().map(field -> "'" + field.getName() + "'," + field.getName()).collect(Collectors.joining(",")) + ")";
    }

    public Logger getLogger() {
        return logger;
    }

    @Override
    public List<Resource> getResources() {
        Map<String, Resource> resources = new HashMap<>();
        String databaseName = this.getConnectionDetails().get(DATABASE);
        String query = """
                SELECT
                    table_schema AS schema_name,
                    table_name,
                    column_name,
                    data_type,
                    is_nullable
                FROM information_schema.columns
                WHERE table_schema = ?
                ORDER BY table_schema, table_name, ordinal_position
                """;

        try (Connection connection = this.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, databaseName);

            try (ResultSet resultSet = stmt.executeQuery()) {
                while (resultSet.next()) {
                    String schemaName = resultSet.getString("schema_name");
                    String tableName = resultSet.getString("table_name");
                    String columnName = resultSet.getString("column_name");
                    String dataType = resultSet.getString("data_type");
                    boolean isNullable = resultSet.getString("is_nullable").equalsIgnoreCase("YES");

                    String resourceKey = schemaName + "." + tableName;
                    Resource resource = resources.computeIfAbsent(resourceKey, key -> {
                        Resource newResource = new Resource();
                        newResource.setResourceName(tableName);
                        newResource.setServer(MySQLServer.this);
                        newResource.setResourceID(resources.size() + 1);
                        newResource.getResourceProperties().put(SCHEMA, schemaName);
                        return newResource;
                    });

                    resource.getFields().add(new Field(columnName, getDataType(dataType), isNullable));
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
            Map<String, List<Resource>> resourcesBySchema = new HashMap<>();

            for (Resource resource : resources) {
                String tableName = resource.getResourceName();
                if (tableName == null || tableName.isBlank()) {
                    throw new RuntimeException("Resource name (table) cannot be null or blank");
                }

                String schema = resource.getResourceProperties().getOrDefault(SCHEMA, this.getConnectionDetails().get(DATABASE));
                if (schema == null || schema.isBlank()) {
                    throw new RuntimeException("Schema property is required for resource '" + tableName + "'");
                }

                resourcesBySchema.computeIfAbsent(schema, key -> new ArrayList<>()).add(resource);
            }

            for (Map.Entry<String, List<Resource>> entry : resourcesBySchema.entrySet()) {
                String schema = entry.getKey();
                Map<String, Resource> tableNameToResource = new HashMap<>();
                for (Resource resource : entry.getValue()) {
                    tableNameToResource.put(resource.getResourceName(), resource);
                }

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

                    try (ResultSet resultSet = stmt.executeQuery()) {
                        String currentTableName = null;
                        List<Field> currentFields = null;

                        while (resultSet.next()) {
                            String tableName = resultSet.getString("table_name");
                            if (!tableNameToResource.containsKey(tableName)) {
                                continue;
                            }

                            if (currentTableName != null && !currentTableName.equals(tableName)) {
                                result.put(currentTableName, currentFields);
                            }

                            if (!tableName.equals(currentTableName)) {
                                currentTableName = tableName;
                                currentFields = new ArrayList<>();
                            }

                            String columnName = resultSet.getString("column_name");
                            String dataType = resultSet.getString("data_type");
                            boolean isNullable = resultSet.getString("is_nullable").equalsIgnoreCase("YES");
                            currentFields.add(new Field(columnName, getDataType(dataType), isNullable));
                        }

                        if (currentTableName != null) {
                            result.put(currentTableName, currentFields);
                        }
                    }
                }
            }
        } catch (SQLException | ExecutionException e) {
            throw new RuntimeException("Error occurred while retrieving fields for resources", e);
        }

        return result;
    }

    @Override
    public Map<String, List<String>> getResourcesDependencies(List<Resource> resources) {
        Map<String, List<String>> dependencyMappings = new HashMap<>();

        if (resources == null || resources.isEmpty()) {
            return dependencyMappings;
        }

        Map<String, Resource> resourceLookup = new HashMap<>();
        for (Resource resource : resources) {
            String schema = resource.getResourceProperties().getOrDefault(SCHEMA, this.getConnectionDetails().get(DATABASE));
            String resourceKey = (schema + "." + resource.getResourceName()).toLowerCase();
            resourceLookup.put(resourceKey, resource);
            dependencyMappings.put(resource.getResourceName(), new ArrayList<>());
        }

        StringBuilder queryBuilder = new StringBuilder("""
                SELECT
                    table_schema,
                    table_name,
                    referenced_table_schema,
                    referenced_table_name
                FROM information_schema.key_column_usage
                WHERE referenced_table_name IS NOT NULL
                  AND table_schema = ?
                  AND table_name IN (
                """);

        for (int i = 0; i < resources.size(); i++) {
            if (i > 0) {
                queryBuilder.append(", ");
            }
            queryBuilder.append("?");
        }
        queryBuilder.append(")");

        try (Connection connection = this.getConnection();
             PreparedStatement stmt = connection.prepareStatement(queryBuilder.toString())) {
            stmt.setString(1, this.getConnectionDetails().get(DATABASE));

            for (int i = 0; i < resources.size(); i++) {
                stmt.setString(i + 2, resources.get(i).getResourceName());
            }

            try (ResultSet resultSet = stmt.executeQuery()) {
                while (resultSet.next()) {
                    String tableName = resultSet.getString("table_name");
                    String referencedSchema = resultSet.getString("referenced_table_schema");
                    String referencedTable = resultSet.getString("referenced_table_name");
                    String lookupKey = (referencedSchema + "." + referencedTable).toLowerCase();

                    Resource referencedResource = resourceLookup.get(lookupKey);
                    if (referencedResource == null) {
                        continue;
                    }

                    List<String> dependencies = dependencyMappings.computeIfAbsent(tableName, key -> new ArrayList<>());
                    if (!dependencies.contains(referencedResource.getResourceName())) {
                        dependencies.add(referencedResource.getResourceName());
                    }
                }
            }

            return dependencyMappings;
        } catch (SQLException | ExecutionException e) {
            throw new RuntimeException("Error occurred while retrieving resource dependencies", e);
        }
    }

    @Override
    public Map<String, Map<String, String>> getPropertiesForMerge(List<Resource> resources) {
        Map<String, Map<String, String>> result = new HashMap<>();

        if (resources == null || resources.isEmpty()) {
            return result;
        }

        StringBuilder queryBuilder = new StringBuilder("""
                SELECT
                    tc.table_name,
                    kcu.column_name,
                    kcu.ordinal_position
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_name = kcu.constraint_name
                 AND tc.table_schema = kcu.table_schema
                 AND tc.table_name = kcu.table_name
                WHERE tc.table_schema = ?
                  AND tc.constraint_type = 'PRIMARY KEY'
                  AND tc.table_name IN (
                """);

        for (int i = 0; i < resources.size(); i++) {
            if (i > 0) {
                queryBuilder.append(", ");
            }
            queryBuilder.append("?");
        }
        queryBuilder.append(") ORDER BY tc.table_name, kcu.ordinal_position");

        Map<String, List<String>> primaryKeysByTable = new HashMap<>();

        try (Connection connection = this.getConnection();
             PreparedStatement statement = connection.prepareStatement(queryBuilder.toString())) {
            statement.setString(1, this.getConnectionDetails().get(DATABASE));

            for (int i = 0; i < resources.size(); i++) {
                statement.setString(i + 2, resources.get(i).getResourceName());
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String tableName = resultSet.getString("table_name");
                    String columnName = resultSet.getString("column_name");
                    primaryKeysByTable.computeIfAbsent(tableName, ignored -> new ArrayList<>()).add(columnName);
                }
            }
        } catch (SQLException | ExecutionException e) {
            throw new RuntimeException("Error occurred while generating merge properties", e);
        }

        for (Resource resource : resources) {
            Map<String, String> mergeProperties = new HashMap<>();
            List<String> primaryKeyColumns = primaryKeysByTable.get(resource.getResourceName());

            if (primaryKeyColumns != null && !primaryKeyColumns.isEmpty()) {
                mergeProperties.put(MergeStrategy.MERGE_MATCH_COLUMNS, String.join(",", primaryKeyColumns));
            }

            result.put(resource.getResourceName(), mergeProperties);
        }

        return result;
    }
}
