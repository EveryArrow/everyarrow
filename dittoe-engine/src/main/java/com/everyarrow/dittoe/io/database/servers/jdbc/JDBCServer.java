package com.everyarrow.dittoe.io.database.servers.jdbc;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.everyarrow.dittoe.io.ResourcePresentation;
import com.everyarrow.dittoe.io.ResourcePresentationProvider;
import com.everyarrow.dittoe.io.ResourcePropertyDescriptor;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.io.database.DatabaseServer;
import com.everyarrow.dittoe.io.ResourceCatalog;
import com.everyarrow.dittoe.io.ResourceDependencyProvider;
import com.everyarrow.dittoe.io.database.servers.jdbc.reader.JDBCReader;
import com.everyarrow.dittoe.io.database.servers.jdbc.writer.JDBCWriter;
import com.everyarrow.dittoe.pipelines.behaviors.instrumentation.Instrumentation;
import com.everyarrow.dittoe.pipelines.behaviors.schemavalidation.FieldsValidator;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.DataType;
import org.everyarrow.dittoe.schema.PipelineMetadata;
import org.everyarrow.dittoe.schema.Resource;
import com.everyarrow.dittoe.utility.Config;
import com.everyarrow.dittoe.utility.metadata.MetadataRepositoryAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Abstract class representing a JDBC-based database server.
 * It provides functionality to test the connection and retrieve resources (tables and columns).
 */
public abstract class JDBCServer extends DatabaseServer implements JDBCServerConnector, ResourceCatalog,
        ResourceDependencyProvider, ResourcePresentationProvider {
    private static final ResourcePresentation RESOURCE_PRESENTATION = new ResourcePresentation(
            "Table",
            List.of(new ResourcePropertyDescriptor("schema", "Schema", true))
    );

    /**
     * Constant for property key "user".
     */
    protected static final String USER = "user";
    /**
     * Constant for property key "password".
     */
    protected static final String PASSWORD = "password";
    /**
     * Constant for property key "host".
     */
    protected static final String HOST = "host";
    /**
     * Constant for property key "port".
     */
    protected static final String PORT = "port";
    /**
     * Constant for property key "database".
     */
    protected static final String DATABASE = "database";

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    /**
     * JDBC Connection Pool.
     */
    private HikariDataSource connectionPool;

    /**
     * Constructor.
     *
     * @throws ExecutionException In case of failure to validate properties or connect
     */
    public JDBCServer(JDBCReader jdbcReader, JDBCWriter jdbcWriter) throws ExecutionException {
        super(jdbcReader, jdbcWriter);
    }

    public static DataType getDataType(int sqlType) throws ExecutionException {
        Optional<Field> field = Arrays.stream(Types.class.getDeclaredFields()).filter(f ->
        {
            try {
                return f.getInt(null) == sqlType;
            } catch (IllegalAccessException ignored) {
                return false;
            }
        }).findFirst();

        if (field.isEmpty())
            throw new ExecutionException(String.format("Could not infer field type. SQL Data Type: %s", sqlType));

        return switch (field.get().getName()) {
            case "SMALLINT", "INTEGER", "BIGINT", "NUMERIC", "BINARY", "VARBINARY", "LONGVARBINARY" -> DataType.Integer;
            case "FLOAT", "REAL", "DECIMAL" -> DataType.Decimal;
            case "DOUBLE" -> DataType.Double;
            case "DATE" -> DataType.Date;
            case "TIME" -> DataType.Time;
            case "TIMESTAMP" -> DataType.DateTime;
            case "TIME_WITH_TIMEZONE" -> DataType.TimeWithTimeZone;
            case "TIMESTAMP_WITH_TIMEZONE" -> DataType.DateTimeWithTimeZone;
            case "NULL", "OTHER", "JAVA_OBJECT", "DISTINCT", "STRUCT", "ARRAY", "BLOB", "CLOB", "REF", "DATALINK",
                 "CHAR", "VARCHAR", "LONGVARCHAR", "ROWID", "NCHAR", "NVARCHAR", "LONGNVARCHAR", "NCLOB", "SQLXML",
                 "REF_CURSOR" -> DataType.String;
            case "BIT", "TINYINT", "BOOLEAN" -> DataType.Boolean;
            default ->
                    throw new ExecutionException(String.format("Could not infer field type. SQL Data Type: %s", sqlType));
        };
    }

    @Override
    public void init() throws ExecutionException {
        super.init();
    }


    public abstract String getSafeResourceName();

    protected abstract String getConnectionString();

    protected abstract String getDriverClassName();

    protected abstract Properties getCredentialProperties();

    protected abstract void validateConnectionProperties() throws ExecutionException;

    public abstract String escapeIdentifierName(String identifierName);

    public Connection getConnection() throws ExecutionException {
        try {
            return getConnectionPool().getConnection();
        } catch (Exception e) {
            throw new ExecutionException("Unable to create database connection", e);
        }
    }

    @Override
    public void close() {
        if (this.connectionPool != null) {
            this.getConnectionPool().close();
            this.connectionPool = null;
        }
    }

    protected Logger getLogger() {
        return logger;
    }

    public HikariDataSource getConnectionPool() {
        if (this.connectionPool == null) {
            try {
                this.connectionPool = this.createConnectionPool();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        return connectionPool;
    }

    public void setConnectionPool(HikariDataSource connectionPool) {
        this.connectionPool = connectionPool;
    }

    @Override
    public void testConnection() {
        try (HikariDataSource connectionPool = getConnectionPool();
             Connection connection = connectionPool.getConnection()) {

            if (!connection.isValid(2)) {
                throw new RuntimeException("Connection is not valid.");
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to test connection", e);
        }
    }

    private HikariDataSource createConnectionPool() throws Exception {
        final int numThreads = Config.PIPELINE_THREADS + 5;

        final String connectionString = this.getConnectionString();
        final HikariConfig hikariConfig = getHikariConfig(connectionString, numThreads);

        return new HikariDataSource(hikariConfig);
    }

    private HikariConfig getHikariConfig(String connectionString, int numThreads) {
        final String driverClassName = this.getDriverClassName();
        final Properties credentialProperties = this.getCredentialProperties();

        final HikariConfig hikariConfig = new HikariConfig();

        hikariConfig.setJdbcUrl(connectionString);
        hikariConfig.setDriverClassName(driverClassName);
        hikariConfig.setMaximumPoolSize(numThreads);
        hikariConfig.setMinimumIdle(1);

        credentialProperties.forEach((key, val) -> hikariConfig.addDataSourceProperty((String) key, val));
        return hikariConfig;
    }

    protected abstract boolean isSystemSchema(String schemaName);

    public String getDataTypeCast(String fieldName, DataType dataType) {
        return "cast(%s as %s)".formatted(fieldName, this.getNativeDataType(dataType));
    }
    public abstract String getNativeDataType(DataType dataType);

    public boolean supportsTargetTableAutoCreate() {
        return false;
    }

    public void createTargetTableIfNotExists(Connection connection,
            List<org.everyarrow.dittoe.schema.Field> fields) throws SQLException {
        throw new UnsupportedOperationException("Target table auto-create is not supported for "
                + this.getClass().getSimpleName());
    }

    protected String buildCreateTableColumnDefinitions(List<org.everyarrow.dittoe.schema.Field> fields) {
        return fields.stream()
                .map(field -> this.escapeIdentifierName(field.getName().trim())
                        + " "
                        + this.getNativeDataType(field.getDataType())
                        + (field.isNullable() ? "" : " not null"))
                .collect(Collectors.joining(", "));
    }

    /**
     * Resolves database specific column type to TAP DataType.
     *
     * @param columnTypeName Type name of the column.
     * @return TAP DataType.
     * @throws ExecutionException In case data type could not be inferred
     */
    public abstract DataType getDataType(String columnTypeName) throws ExecutionException;

    @Override
    public ResourcePresentation getResourcePresentation() {
        return RESOURCE_PRESENTATION;
    }

    /**
     * Retrieves the list of resources (tables and their columns) from the database.
     *
     * @return List of {@link Resource} representing database tables and columns.
     */
    @Override
    public List<Resource> getResources() {
        try (Connection connection = this.getConnection()) {

            Map<String, Resource> resources = new HashMap<>();
            DatabaseMetaData metaData = connection.getMetaData();

            try (ResultSet resultSet = metaData.getColumns(null, null, null, null)) {
                AtomicInteger counter = new AtomicInteger(1);

                while (resultSet.next()) {
                    String tableName = resultSet.getString("TABLE_NAME");
                    String tableSchema = resultSet.getString("TABLE_SCHEM");

                    if (tableSchema != null && this.isSystemSchema(tableSchema)) {
                        continue; // Skip system schemas
                    }

                    // Create or update resource for the table
                    String resourceKey = tableSchema + "." + tableName;
                    Resource resource = resources.computeIfAbsent(resourceKey, k -> {
                        Resource newResource = new Resource();
                        newResource.setResourceName(tableName);
                        newResource.setServer(JDBCServer.this);
                        newResource.setResourceID(counter.getAndIncrement());
                        newResource.getResourceProperties().put("schema", tableSchema);
                        return newResource;
                    });

                    // Add field (column) information to the resource
                    resource.getFields().add(new org.everyarrow.dittoe.schema.Field(
                            resultSet.getString("COLUMN_NAME"),
                            getDataType(resultSet.getString("TYPE_NAME")),
                            resultSet.getInt("NULLABLE") == DatabaseMetaData.columnNullable));
                }
            }

            for (Resource resource : resources.values()) {
                addConstraintMetadata(metaData, resource);
            }

            return resources.values().stream().toList();

        } catch (SQLException | ExecutionException e) {
            throw new RuntimeException("Error occurred while retrieving resources", e);
        }
    }

    private static void addConstraintMetadata(DatabaseMetaData metaData, Resource resource) throws SQLException {
        String schema = resource.getResourceProperties().get("schema");
        String tableName = resource.getResourceName();
        List<String> primaryKeys = new ArrayList<>();
        List<String> foreignKeys = new ArrayList<>();

        try (ResultSet resultSet = metaData.getPrimaryKeys(null, schema, tableName)) {
            while (resultSet.next()) {
                primaryKeys.add(resultSet.getString("COLUMN_NAME"));
            }
        }
        try (ResultSet resultSet = metaData.getImportedKeys(null, schema, tableName)) {
            while (resultSet.next()) {
                String column = resultSet.getString("FKCOLUMN_NAME");
                if (!foreignKeys.contains(column)) {
                    foreignKeys.add(column);
                }
            }
        }

        resource.getResourceProperties().put("primaryKeys", String.join(",", primaryKeys));
        resource.getResourceProperties().put("foreignKeys", String.join(",", foreignKeys));
        resource.getResourceProperties().put("constraintMetadata", "jdbc");
        try (ResultSet resultSet = metaData.getTables(null, schema, tableName, null)) {
            if (resultSet.next()) {
                resource.getResourceProperties().put("resourceType", resultSet.getString("TABLE_TYPE"));
            }
        }
    }

    /**
     * Validates the existence of the table specified in the resource.
     *
     * @throws Exception Thrown if validation fails or the table does not exist.
     */
    protected void validateTableName() throws Exception {
        DataStoreInterface dataStoreInterface = DataStoreInterface.getDataStoreInterface();
        PipelineMetadata threadLocalPipelineMetadata = MetadataRepositoryAPI.getPipelineMetadata();
        Instrumentation threadLocalInstrumentation = Instrumentation.getInstance();
        String pipelineMetadataRepositoryAPIKey = MetadataRepositoryAPI.getApiKey();
        String instrumentationAPIKey = Instrumentation.getApiKey();

        this.getRetry().execute(() -> {
            DataStoreInterface.setDataStoreInterface(dataStoreInterface);
            MetadataRepositoryAPI.setPipelineMetadata(threadLocalPipelineMetadata);
            MetadataRepositoryAPI.setApiKey(pipelineMetadataRepositoryAPIKey);
            Instrumentation.setInstance(threadLocalInstrumentation);
            Instrumentation.setApiKey(instrumentationAPIKey);

            try (Connection connection = this.getConnection();
                 ResultSet tables = connection.getMetaData().getTables(null, null,
                         this.getResource().getResourceName(), null)) {

                if (!tables.next()) {
                    throw new ExecutionException("Table does not exist: " + this.getResource().getResourceName());
                }
            } catch (SQLException | ExecutionException e) {
                throw new ExecutionException("Could not validate table name.", e);
            }

            return null;
        });
    }

    /**
     * Validates the fields of the resource against the database schema.
     *
     * @throws ExecutionException Thrown if validation fails.
     */
    public void validateFields() throws ExecutionException {
        FieldsValidator fieldsValidator = new FieldsValidator(this.getResource().getFields(), this.getResource().getFields());
        String validationError = fieldsValidator.getValidationError();
        if (validationError != null) {
            throw new ExecutionException("Validation error - " + validationError);
        }
    }

    public abstract String getJsonBuilderQuery();

    @Override
    public Map<String, List<String>> getResourcesDependencies(List<Resource> resources) {
        Map<String, List<String>> dependencyMappings = new HashMap<>();
        Map<String, Resource> resourceLookup = new HashMap<>();

        for (Resource resource : resources) {
            String resourceKey = (resource.getResourceProperties().get("schema") + "." + resource.getResourceName()).toLowerCase(Locale.ROOT);
            resourceLookup.put(resourceKey, resource);
        }

        try (Connection connection = this.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();

            for (Resource resource : resources) {
                String schema = resource.getResourceProperties().get("schema");
                String resourceName = resource.getResourceName();

                List<String> dependencies = new ArrayList<>();

                try (ResultSet foreignKeys = metaData.getImportedKeys(null, schema, resourceName)) {
                    while (foreignKeys.next()) {
                        String pkSchema = foreignKeys.getString("PKTABLE_SCHEM");
                        String pkTable = foreignKeys.getString("PKTABLE_NAME");

                        String lookupKey = (pkSchema + "." + pkTable).toLowerCase(Locale.ROOT);

                        Resource referencedResource = resourceLookup.get(lookupKey);
                        if (referencedResource != null) {
                            String dependencyResourceName = referencedResource.getResourceName();
                            if (!dependencies.contains(dependencyResourceName)) {
                                dependencies.add(dependencyResourceName);
                            }
                        }
                    }
                }
                dependencyMappings.put(resourceName, dependencies);
            }

        } catch (SQLException | ExecutionException e) {
            throw new RuntimeException("Error occurred while retrieving resource dependencies", e);
        }
        return dependencyMappings;
    }
}
