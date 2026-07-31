package com.everyarrow.dittoe.io.database.servers.sqlite;

import org.checkerframework.checker.nullness.qual.NonNull;
import com.everyarrow.dittoe.io.OrchestrationMetadataProvider;
import com.everyarrow.dittoe.io.ResourcePresentation;
import com.everyarrow.dittoe.io.database.servers.jdbc.JDBCServer;
import com.everyarrow.dittoe.io.database.servers.sqlite.reader.SQLiteReader;
import com.everyarrow.dittoe.io.database.servers.sqlite.writer.SQLiteWriter;
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

public class SQLiteServer extends JDBCServer implements OrchestrationMetadataProvider {
    private static final ResourcePresentation RESOURCE_PRESENTATION = new ResourcePresentation("Table", List.of());

    /**
     * Constant for property key "path".
     */
    protected static final String PATH = "path";

    public SQLiteServer(Boolean isSource) throws ExecutionException {
        super(isSource ? new SQLiteReader() : null, isSource ? null : new SQLiteWriter());

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
        // SQLite doesn't use schemas, just escape the table name
        return this.escapeIdentifierName(this.getResource().getResourceName());
    }

    @Override
    public String escapeIdentifierName(String identifierName) {
        // SQLite uses double quotes or square brackets for identifiers
        return "\""
                .concat(identifierName
                        .replaceAll("\"", "\"\""))
                .concat("\"");
    }

    @Override
    public boolean supportsTargetTableAutoCreate() {
        return true;
    }

    @Override
    public void createTargetTableIfNotExists(Connection connection, List<Field> fields) throws SQLException {
        String sql = "create table if not exists "
                + this.getSafeResourceName()
                + " ("
                + this.buildCreateTableColumnDefinitions(fields)
                + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        }
    }

    @Override
    protected void validateConnectionProperties() throws ExecutionException {
        this.validateConnectionStringProperties();
        // SQLite doesn't require credentials
    }

    @Override
    protected Properties getCredentialProperties() {
        // SQLite doesn't use credentials
        return new Properties();
    }

    @Override
    protected String getConnectionString() {
        Map<String, String> properties = this.getConnectionDetails();
        String databasePath = properties.get(PATH);
        
        if (databasePath == null || databasePath.isEmpty()) {
            throw new RuntimeException("path property must be provided");
        }
        
        return "jdbc:sqlite:" + databasePath;
    }

    @Override
    protected String getDriverClassName() {
        return "org.sqlite.JDBC";
    }

    @Override
    public String getNativeDataType(DataType dataType) {
        return switch (dataType) {
            case DataType.UUID, DataType.XML, DataType.DateTime, DataType.DateTimeWithTimeZone, DataType.Time,
                 DataType.TimeWithTimeZone, DataType.String, DataType.Date, DataType.JSON, DataType.JSONB,
                 DataType.Array -> "TEXT";
            case DataType.Integer, DataType.Boolean -> "INTEGER";
            case DataType.Decimal, DataType.Double, DataType.Number -> "REAL";
            case DataType.Bytes -> "BLOB";
        };
    }

    @Override
    public DataType getDataType(@NonNull String columnTypeName) throws ExecutionException {
        String lowerTypeName = columnTypeName.toLowerCase();
        
        DataType dataType;
        if (lowerTypeName.contains("int")) {
            dataType = DataType.Integer;
        } else if (lowerTypeName.equals("text") || lowerTypeName.contains("char") || lowerTypeName.contains("bpchar") ||
                   lowerTypeName.equals("clob") || lowerTypeName.equals("uuid") || lowerTypeName.equals("bytea") || 
                   lowerTypeName.equals("xml")) {
            dataType = DataType.String;
        } else if (lowerTypeName.equals("blob")) {
            dataType = DataType.Bytes;
        } else if (lowerTypeName.equals("real") || lowerTypeName.equals("double") || 
                   lowerTypeName.equals("double precision") || lowerTypeName.equals("float") || 
                   lowerTypeName.equals("numeric") || lowerTypeName.equals("money")) {
            dataType = DataType.Decimal;
        } else if (lowerTypeName.contains("bool")) {
            dataType = DataType.Boolean;
        } else if (lowerTypeName.equals("date")) {
            dataType = DataType.Date;
        } else if ( lowerTypeName.equals("time")) {
            dataType = DataType.Time;
        } else if (lowerTypeName.equals("datetime") ||
                   lowerTypeName.equals("timestamp")) {
            dataType = DataType.DateTime;
        } else {
            this.getLogger().info("Default data type is being used because the field data type could not be matched. " +
                    "Field data type: " + columnTypeName);
            dataType = DataType.String;
        }

        return dataType;
    }

    private void validateConnectionStringProperties() throws ExecutionException {
        String[] keys = new String[]{PATH};
        Map<String, String> connectionDetails = this.getConnectionDetails();

        for (String key : keys) {
            if (!connectionDetails.containsKey(key))
                throw new ExecutionException("%s property must be provided in server".formatted(key));
        }
    }

    @Override
    protected boolean isSystemSchema(String schemaName) {
        // SQLite doesn't have schemas in the same way as PostgreSQL
        // System tables start with sqlite_
        return schemaName != null && schemaName.startsWith("sqlite_");
    }

    @Override
    public String getJsonBuilderQuery() {
        // SQLite has json_object function for building JSON
        return "json_object(*)";
    }

    @Override
    public ResourcePresentation getResourcePresentation() {
        return RESOURCE_PRESENTATION;
    }

    /**
     * Override the getResources method to use SQLite-specific queries.
     */
    @Override
    public List<Resource> getResources() {
        try (Connection connection = this.getConnection()) {
            Map<String, Resource> resources = new HashMap<>();

            // SQLite uses PRAGMA table_info to get column information
            String query = """
                    SELECT name FROM sqlite_master
                    WHERE type='table' 
                    AND name NOT LIKE 'sqlite_%'
                    ORDER BY name
                    """;

            try (PreparedStatement stmt = connection.prepareStatement(query);
                 ResultSet resultSet = stmt.executeQuery()) {

                AtomicInteger counter = new AtomicInteger(1);

                while (resultSet.next()) {
                    String tableName = resultSet.getString("name");
                    
                    Resource resource = new Resource();
                    resource.setResourceName(tableName);
                    resource.setServer(SQLiteServer.this);
                    resource.setResourceID(counter.getAndIncrement());
                    
                    // Get column information for this table
                    String columnQuery = "PRAGMA table_info(" + tableName + ")";
                    try (PreparedStatement colStmt = connection.prepareStatement(columnQuery);
                         ResultSet colResultSet = colStmt.executeQuery()) {
                        
                        while (colResultSet.next()) {
                            String columnName = colResultSet.getString("name");
                            String dataType = colResultSet.getString("type");
                            boolean isNullable = colResultSet.getInt("notnull") == 0;
                            
                            resource.getFields().add(new Field(
                                    columnName,
                                    getDataType(dataType),
                                    isNullable
                            ));
                        }
                    }
                    
                    resources.put(tableName, resource);
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
            for (Resource resource : resources) {
                String tableName = resource.getResourceName();
                if (tableName == null || tableName.isBlank()) {
                    throw new RuntimeException("Resource name (table) cannot be null or blank");
                }

                List<Field> fields = new ArrayList<>();
                
                // Use PRAGMA table_info to get column information
                String query = "PRAGMA table_info(" + tableName + ")";
                try (PreparedStatement stmt = connection.prepareStatement(query);
                     ResultSet rs = stmt.executeQuery()) {
                    
                    while (rs.next()) {
                        String columnName = rs.getString("name");
                        String dataType = rs.getString("type");
                        boolean isNullable = rs.getInt("notnull") == 0;
                        
                        fields.add(new Field(columnName, getDataType(dataType), isNullable));
                    }
                }
                
                result.put(tableName, fields);
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
            for (Resource resource : resources) {
                String resourceName = resource.getResourceName();
                Map<String, String> mergeProperties = new HashMap<>();
                
                // SQLite doesn't have the same constraint system as PostgreSQL
                // For now, we'll leave merge properties empty
                // Strategies will need to be implemented separately
                getLogger().info("Merge properties not yet implemented for SQLite table {}", resourceName);
                
                result.put(resourceName, mergeProperties);
            }

            getLogger().info("Generated merge properties for {} resources", result.size());
        } catch (Exception e) {
            getLogger().error("Error occurred while generating merge properties: {}", e.getMessage(), e);
        }

        return result;
    }
}
