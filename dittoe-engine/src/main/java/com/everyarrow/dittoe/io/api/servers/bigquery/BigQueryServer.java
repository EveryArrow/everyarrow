package com.everyarrow.dittoe.io.api.servers.bigquery;

import org.everyarrow.dittoe.schema.Field;
import com.everyarrow.dittoe.io.OrchestrationMetadataProvider;
import com.everyarrow.dittoe.io.ResourcePresentation;
import com.everyarrow.dittoe.io.ResourcePropertyDescriptor;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.bigquery.*;
import com.everyarrow.dittoe.io.api.APIServer;
import com.everyarrow.dittoe.io.api.servers.bigquery.reader.BigQueryReader;
import com.everyarrow.dittoe.io.api.servers.bigquery.writer.BigQueryWriter;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import com.everyarrow.dittoe.utility.security.SqlSafety;
import org.everyarrow.dittoe.schema.DataType;
import org.everyarrow.dittoe.schema.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.everyarrow.utilities.property.PropertyManagement.validateProperties;

/**
 * Abstract class representing a JDBC-based database server.
 * It provides functionality to test the connection and retrieve resources (tables and columns).
 */
public class BigQueryServer extends APIServer implements BigQueryServerConnector, OrchestrationMetadataProvider {
    private static final ResourcePresentation RESOURCE_PRESENTATION = new ResourcePresentation(
            "Table",
            List.of(new ResourcePropertyDescriptor("dataset", "Dataset", true))
    );
    private static final Map<DataType, StandardSQLTypeName> dataTypeToStandardSQLTypeMap = new LinkedHashMap<>();
    private static final Map<StandardSQLTypeName, DataType> standardSQLTypeToDataTypeMap = new LinkedHashMap<>();

    static {
        populateDataTypeConversionMap();
    }
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private BigQuery bigQueryClient;

    public BigQueryServer(Boolean isSource) throws ExecutionException {
        super(isSource ? new BigQueryReader() : null, isSource ? null : new BigQueryWriter());

        if (isSource)
            this.getReader().setServer(this);
        else
            this.getWriter().setServer(this);
    }

    public static StandardSQLTypeName dataTypeToStandardSQLType(DataType dataType) {
        return dataTypeToStandardSQLTypeMap.get(dataType);
    }

    public static DataType standardSQLTypeToDataType(StandardSQLTypeName standardSQLType) {
        return standardSQLTypeToDataTypeMap.get(standardSQLType);
    }

    @Override
    public ResourcePresentation getResourcePresentation() {
        return RESOURCE_PRESENTATION;
    }

    @Override
    public void init() throws ExecutionException {
        this.populateDataTypeConversionMap();

        validateProperties(this.getConnectionDetails(), "key");

        try {
            this.bigQueryClient = this.createBigQueryService();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        super.init();
    }

    public GoogleCredentials getCredentials() {
        try {
            return GoogleCredentials.fromStream(new ByteArrayInputStream(
                    Base64.getDecoder().decode(
                            this.getConnectionDetails().get("key")
                    )));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public BigQuery createBigQueryService() throws Exception {
        return this.getRetry().execute(() -> BigQueryOptions
                .newBuilder()
                .setCredentials(this.getCredentials())
                .build()
                .getService()
        );
    }

    private static void populateDataTypeConversionMap() {
        dataTypeToStandardSQLTypeMap.put(DataType.String, StandardSQLTypeName.STRING);
        dataTypeToStandardSQLTypeMap.put(DataType.Integer, StandardSQLTypeName.INT64);
        dataTypeToStandardSQLTypeMap.put(DataType.Double, StandardSQLTypeName.FLOAT64);
        dataTypeToStandardSQLTypeMap.put(DataType.Decimal, StandardSQLTypeName.NUMERIC);
        dataTypeToStandardSQLTypeMap.put(DataType.Boolean, StandardSQLTypeName.BOOL);
        dataTypeToStandardSQLTypeMap.put(DataType.Date, StandardSQLTypeName.DATE);
        dataTypeToStandardSQLTypeMap.put(DataType.DateTime, StandardSQLTypeName.DATETIME);
        dataTypeToStandardSQLTypeMap.put(DataType.DateTimeWithTimeZone, StandardSQLTypeName.TIMESTAMP);
        dataTypeToStandardSQLTypeMap.put(DataType.Time, StandardSQLTypeName.TIME);
        dataTypeToStandardSQLTypeMap.put(DataType.TimeWithTimeZone, StandardSQLTypeName.TIME);
        dataTypeToStandardSQLTypeMap.put(DataType.UUID, StandardSQLTypeName.STRING);
        dataTypeToStandardSQLTypeMap.put(DataType.JSON, StandardSQLTypeName.JSON);
        dataTypeToStandardSQLTypeMap.put(DataType.JSONB, StandardSQLTypeName.JSON);
        dataTypeToStandardSQLTypeMap.put(DataType.Number, StandardSQLTypeName.NUMERIC);
        dataTypeToStandardSQLTypeMap.put(DataType.Bytes, StandardSQLTypeName.BYTES);

        dataTypeToStandardSQLTypeMap.forEach((key, value) -> standardSQLTypeToDataTypeMap.putIfAbsent(value, key));
    }

    public BigQuery getBigQueryClient() {
        return bigQueryClient;
    }

    public void setBigQueryClient(BigQuery bigQueryClient) {
        this.bigQueryClient = bigQueryClient;
    }

    public Logger getLogger() {
        return logger;
    }

    public String getSafeResourceName() {
        return SqlSafety.quoteBigQueryPath(
                getResource().getServer().getConnectionDetails().get("project"),
                getResource().getResourceProperties().get("dataset"),
                getResource().getResourceName());
    }

    public String escapeIdentifierName(String identifierName) {
        return SqlSafety.quoteBigQueryIdentifier(identifierName);
    }

    public String getDataTypeCast(DataType dataType) {
        return switch (dataType) {
            case String, UUID -> "::STRING";
            case Integer -> "::INT64";
            case Double -> "::FLOAT64";
            case Decimal, Number -> "::NUMERIC";
            case Boolean -> "::BOOL";
            case Date -> "::DATE";
            case DateTime -> "::DATETIME";
            case DateTimeWithTimeZone -> "::TIMESTAMP";
            case Time, TimeWithTimeZone -> "::TIME";
            case JSON, JSONB -> "::JSON";
            default -> "";
        };
    }

    private String buildAllPrimaryKeyQuery(String projectId) {
        StringBuilder queryBuilder = new StringBuilder();
        try {
            // Get all datasets in the project
            List<String> datasets = new ArrayList<>();
            for (Dataset dataset : this.getBigQueryClient().listDatasets(projectId).iterateAll()) {
                datasets.add(dataset.getDatasetId().getDataset());
            }

            if (datasets.isEmpty()) {
                return "SELECT NULL as table_schema, NULL as table_name, NULL as column_name, NULL as ordinal_position WHERE FALSE";
            }

            // Build UNION query for all datasets
            for (int i = 0; i < datasets.size(); i++) {
                if (i > 0) {
                    queryBuilder.append(" UNION ALL ");
                }

                String datasetName = datasets.get(i);
                String datasetPath = SqlSafety.quoteBigQueryPath(projectId, datasetName);
                queryBuilder.append(String.format("""
                        (
                        SELECT
                            k.table_schema,
                            k.table_name,
                            k.column_name,
                            k.ordinal_position
                        FROM %s.INFORMATION_SCHEMA.KEY_COLUMN_USAGE AS k
                        JOIN %s.INFORMATION_SCHEMA.TABLE_CONSTRAINTS AS c
                            ON k.constraint_name = c.constraint_name
                            AND k.constraint_schema = c.constraint_schema
                            AND k.table_name = c.table_name
                        WHERE c.constraint_type = 'PRIMARY KEY'
                        )
                        """, datasetPath, datasetPath));
            }

            queryBuilder.append(" ORDER BY table_schema, table_name, ordinal_position");

        } catch (Exception e) {
            logger.error("Error building primary key query", e);
            return "SELECT NULL as table_schema, NULL as table_name, NULL as column_name, NULL as ordinal_position WHERE FALSE";
        }

        return queryBuilder.toString();
    }

    private Map<String, List<String>> getAllPrimaryKeyColumns(String projectId) {
        Map<String, List<String>> allPrimaryKeyColumns = new HashMap<>();

        try {
            // Build query to get all primary key constraints across all datasets
            String primaryKeyQuery = buildAllPrimaryKeyQuery(projectId);

            if (primaryKeyQuery.contains("WHERE FALSE")) {
                logger.info("No datasets found for primary key query");
                return allPrimaryKeyColumns;
            }

            QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(primaryKeyQuery).build();
            TableResult result = this.getBigQueryClient().query(queryConfig);

            // Group results by table
            Map<String, List<String>> tempMap = new HashMap<>();
            for (FieldValueList row : result.iterateAll()) {
                String datasetName = row.get("table_schema").getStringValue();
                String tableName = row.get("table_name").getStringValue();
                String columnName = row.get("column_name").getStringValue();
                long ordinalPosition = row.get("ordinal_position").getLongValue();

                String tableKey = datasetName + "." + tableName;
                tempMap.computeIfAbsent(tableKey, k -> new ArrayList<>()).add(columnName + ":" + ordinalPosition);
            }

            // Sort columns by ordinal position and extract column names
            for (Map.Entry<String, List<String>> entry : tempMap.entrySet()) {
                List<String> sortedColumns = entry.getValue().stream().sorted((a, b) -> {
                    int posA = Integer.parseInt(a.split(":")[1]);
                    int posB = Integer.parseInt(b.split(":")[1]);
                    return Integer.compare(posA, posB);
                }).map(col -> col.split(":")[0]).collect(Collectors.toList());

                allPrimaryKeyColumns.put(entry.getKey(), sortedColumns);


            }
            logger.info("Retrieved primary key information for {} tables", allPrimaryKeyColumns.size());
        } catch (Exception e) {
            logger.warn("Failed to query all primary key constraints: {}", e.getMessage());
            // Return empty map rather than failing the entire operation
        }

        return allPrimaryKeyColumns;
    }

    @Override
    public List<Resource> getResources() {
        try {
            List<Resource> resources = new LinkedList<>();
            String projectId = this.getConnectionDetails().get("project");
            BigQuery bigQuery = this.getBigQueryClient();

            // Get all primary key columns for all tables in one query
            Map<String, List<String>> allPrimaryKeyColumns = getAllPrimaryKeyColumns(projectId);

            for (Dataset dataset : bigQuery.listDatasets(projectId).iterateAll()) {
                String datasetName = dataset.getDatasetId().getDataset();

                int counter = 1;
                // Iterate through all tables in the dataset
                for (Table table : bigQuery.listTables(datasetName).iterateAll()) {
                    table = bigQuery.getTable(table.getTableId());
                    Resource resource = new Resource();
                    resource.setResourceID(counter++);
                    resource.setServer(this);
                    resource.getResourceProperties().put("dataset", datasetName);
                    String tableName = table.getTableId().getTable();
                    resource.setResourceName(tableName);

                    Schema schema = table.getDefinition().getSchema();

                    if (schema == null) {
                        this.getLogger().error("Schema not found for dataset: {} table {}", datasetName, tableName);
                        continue;
                    }

                    // Get primary key columns from the pre-loaded map
//                    String tableKey = datasetName + "." + tableName;
//                    List<String> primaryKeyColumns = allPrimaryKeyColumns.getOrDefault(tableKey, new ArrayList<>());
//                    if (!primaryKeyColumns.isEmpty()) {
//                        resource.getResourceProperties().put(BigQueryWriter.PRIMARY_KEY_COLUMNS, String.join(",", primaryKeyColumns));
//                        resource.getResourceProperties().put(BigQueryWriter.MERGE, "true");
//                        logger.info("Found primary key columns for table {}: {}", tableKey, primaryKeyColumns);
//                    }

                    resources.add(resource);
                }
            }

            return resources;
        } catch (Exception e) {
            throw new RuntimeException("Error occurred while retrieving resources", e);
        }
    }


    /**
     * Gets fields for multiple resources in an optimized manner.
     * Groups resources by dataset to minimize API calls.
     * All resources must have a dataset property set.
     *
     * @param resources List of resources to get fields for
     * @return Map of resource name to list of fields
     */
    @Override
    public Map<String, List<Field>> getResourcesFields(List<Resource> resources) {
        Map<String, List<Field>> result = new HashMap<>();

        if (resources == null || resources.isEmpty()) {
            return result;
        }

        try {
            BigQuery bigQuery = this.getBigQueryClient();

            // Group resources by dataset for efficient batch processing
            Map<String, List<Resource>> resourcesByDataset = new HashMap<>();

            for (Resource resource : resources) {
                String tableName = resource.getResourceName();
                if (tableName == null || tableName.isBlank()) {
                    throw new RuntimeException("Resource name (table) cannot be null or blank");
                }

                String dataset = resource.getResourceProperties().get("dataset");
                if (dataset == null || dataset.isBlank()) {
                    throw new RuntimeException("Dataset property is required for resource '" + tableName + "'");
                }

                resourcesByDataset.computeIfAbsent(dataset, k -> new ArrayList<>()).add(resource);
            }

            // Process resources grouped by dataset
            for (Map.Entry<String, List<Resource>> entry : resourcesByDataset.entrySet()) {
                String dataset = entry.getKey();
                List<Resource> datasetResources = entry.getValue();

                // Build a map of table names for quick lookup
                Map<String, Resource> tableNameToResource = new HashMap<>();
                for (Resource resource : datasetResources) {
                    tableNameToResource.put(resource.getResourceName(), resource);
                }

                // Fetch all tables in this dataset in one call
                try {
                    for (Table table : bigQuery.listTables(dataset).iterateAll()) {
                        String tableName = table.getTableId().getTable();
                        Resource resource = tableNameToResource.get(tableName);

                        if (resource != null) {
                            // Get full table details with schema
                            Table fullTable = bigQuery.getTable(table.getTableId());
                            List<Field> fields = extractFieldsFromTable(fullTable, tableName);
                            if (fields != null) {
                                result.put(tableName, fields);
                            }
                        }
                    }
                } catch (Exception e) {
                    this.getLogger().warn("Failed to list tables in dataset {}: {}", dataset, e.getMessage());
                }
            }

        } catch (Exception e) {
            this.getLogger().error("Failed to get fields for multiple resources: {}", e.getMessage());
        }

        return result;
    }

    /**
     * Helper method to extract fields from a BigQuery table.
     *
     * @param table     The BigQuery table
     * @param tableName The table name (for logging)
     * @return List of fields or null if schema is not available
     */
    private List<Field> extractFieldsFromTable(Table table, String tableName) {
        if (table == null) {
            return null;
        }

        Schema schema = table.getDefinition().getSchema();
        if (schema == null) {
            this.getLogger().warn("Schema not found for table: {}", tableName);
            return new ArrayList<>();
        }

        return schema.getFields().stream()
                .map(field -> {
                    boolean isNullable = field.getMode() == null ||
                            field.getMode().equals(com.google.cloud.bigquery.Field.Mode.NULLABLE);
                    return new Field(
                            field.getName(),
                            standardSQLTypeToDataType(field.getType().getStandardType()),
                            isNullable
                    );
                })
                .collect(Collectors.toList());
    }

    @Override
    public void testConnection() {
        try {
            this.getBigQueryClient().listDatasets().iterateAll();
        } catch (Exception e) {
            throw new RuntimeException("Failed to test connection", e);
        }
    }

    public TableId getTableId() {
        return TableId.of(getResource().getServer().getConnectionDetails().get("project"),
                getResource().getResourceProperties().get("dataset"),
                getResource().getResourceName());
    }

    public Map<String, List<String>> getResourcesDependencies(List<Resource> resources) {
        try {
            Map<String, List<String>> dependencyMappings = new HashMap<>();
            String projectId = this.getConnectionDetails().get("project");

            // Create a map for quick resource lookup by table name
            Map<String, Resource> resourceMap = new HashMap<>();
            for (Resource resource : resources) {
                String tableName = resource.getResourceName();
                String datasetName = resource.getResourceProperties().get("dataset");
                String fullTableName = datasetName + "." + tableName;
                resourceMap.put(fullTableName, resource);
            }

            // Execute the foreign key dependency query
            String dependencyQuery = buildResourceDependencyQuery(projectId);
            QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(dependencyQuery).build();
            TableResult result = this.getBigQueryClient().query(queryConfig);

            // Process the results to build dependency mappings
            for (FieldValueList row : result.iterateAll()) {
                String fkTable = row.get("fk_table").getStringValue();
                String referencedTable = row.get("referenced_table").getStringValue();
                String constraintName = row.get("constraint_name").getStringValue();

                // Find the dataset for these tables
                String fkTableKey = findResourceKey(resourceMap, fkTable);
                String referencedTableKey = findResourceKey(resourceMap, referencedTable);

                if (fkTableKey != null && referencedTableKey != null) {
                    // Get the actual resource objects
                    Resource fkResource = resourceMap.get(fkTableKey);
                    Resource referencedResource = resourceMap.get(referencedTableKey);
                    
                    // Get or create dependency list for the foreign key table using resource name
                    List<String> dependencies = dependencyMappings.computeIfAbsent(fkResource.getResourceName(), k -> new ArrayList<>());

                    // Add the referenced table as a dependency if not already present
                    String referencedResourceName = referencedResource.getResourceName();
                    if (!dependencies.contains(referencedResourceName)) {
                        dependencies.add(referencedResourceName);
                    }
                }
            }

            this.getLogger().info("Found {} resource dependencies", dependencyMappings.size());

            return dependencyMappings;

        } catch (Exception e) {
            this.getLogger().error("Error retrieving resource dependencies", e);
            throw new RuntimeException(e);
        }
    }

    private String buildResourceDependencyQuery(String projectId) {
        // Build the query dynamically to include all datasets in the project
        StringBuilder queryBuilder = new StringBuilder();

        try {
            // Get all datasets in the project
            List<String> datasets = new ArrayList<>();
            for (Dataset dataset : this.getBigQueryClient().listDatasets(projectId).iterateAll()) {
                datasets.add(dataset.getDatasetId().getDataset());
            }

            if (datasets.isEmpty()) {
                return "SELECT NULL as fk_table, NULL as referenced_table, NULL as constraint_name WHERE FALSE";
            }

            // Build UNION query for all datasets
            for (int i = 0; i < datasets.size(); i++) {
                if (i > 0) {
                    queryBuilder.append(" UNION ALL ");
                }

                String datasetName = datasets.get(i);
                String datasetPath = SqlSafety.quoteBigQueryPath(projectId, datasetName);
                queryBuilder.append(String.format("""
                                (
                                WITH fk_constraints AS (
                                  SELECT *
                                  FROM %s.INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                                  WHERE constraint_type = 'FOREIGN KEY'
                                ),
                                fk_cols AS (
                                  SELECT
                                    k.constraint_name,
                                    k.table_name        AS fk_table,
                                    k.column_name       AS fk_column,
                                    k.ordinal_position  AS fk_ordinal,
                                    k.position_in_unique_constraint AS pk_pos
                                  FROM %s.INFORMATION_SCHEMA.KEY_COLUMN_USAGE AS k
                                  JOIN fk_constraints c
                                    ON k.constraint_name = c.constraint_name
                                   AND k.constraint_schema = c.constraint_schema
                                   AND k.table_name = c.table_name
                                ),
                                ref_tables AS (
                                  SELECT
                                    c.constraint_name,
                                    c.table_name AS pk_table
                                  FROM %s.INFORMATION_SCHEMA.CONSTRAINT_COLUMN_USAGE AS c
                                  JOIN fk_constraints fc
                                    ON c.constraint_name = fc.constraint_name
                                   AND c.constraint_schema = fc.constraint_schema
                                  GROUP BY c.constraint_name, c.table_name
                                ),
                                pk_constraints AS (
                                  SELECT table_name AS pk_table, constraint_name AS pk_constraint_name
                                  FROM %s.INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                                  WHERE constraint_type = 'PRIMARY KEY'
                                ),
                                pk_cols AS (
                                  SELECT
                                    k.constraint_name,
                                    k.table_name AS pk_table,
                                    k.column_name AS pk_column,
                                    k.ordinal_position AS pk_ordinal
                                  FROM %s.INFORMATION_SCHEMA.KEY_COLUMN_USAGE AS k
                                  JOIN pk_constraints p
                                    ON k.constraint_name = p.pk_constraint_name
                                   AND k.table_name      = p.pk_table
                                )
                                SELECT
                                  fc.fk_table,
                                  rt.pk_table        AS referenced_table,
                                  fc.constraint_name
                                FROM fk_cols fc
                                JOIN ref_tables rt
                                  USING (constraint_name)
                                JOIN pk_constraints pc
                                  ON pc.pk_table = rt.pk_table
                                JOIN pk_cols pk
                                  ON pk.constraint_name = pc.pk_constraint_name
                                 AND pk.pk_ordinal      = fc.pk_pos
                                )
                                """, datasetPath, datasetPath, datasetPath, datasetPath, datasetPath));
            }

            queryBuilder.append(" ORDER BY fk_table, constraint_name");

        } catch (Exception e) {
            this.getLogger().error("Error building dependency query", e);
            return "SELECT NULL as fk_table, NULL as referenced_table, NULL as constraint_name WHERE FALSE";
        }

        return queryBuilder.toString();
    }

    private String findResourceKey(Map<String, Resource> resourceMap, String tableName) {
        // Try to find the resource key that matches the table name
        for (String key : resourceMap.keySet()) {
            if (key.endsWith("." + tableName)) {
                return key;
            }
        }
        return null;
    }

    /**
     * Gets primary key columns for a specific table
     *
     * @param projectId   The project ID
     * @param datasetName The dataset name
     * @param tableName   The table name
     * @return List of primary key column names in ordinal order
     */
    private List<String> getPrimaryKeyColumnsForTable(String projectId, String datasetName, String tableName) {
        List<String> primaryKeyColumns = new ArrayList<>();

        try {
            String datasetPath = SqlSafety.quoteBigQueryPath(projectId, datasetName);
            String quotedTableName = SqlSafety.quoteSqlLiteral(tableName);
            String primaryKeyQuery = String.format("""
                    SELECT
                        k.column_name,
                        k.ordinal_position
                    FROM %s.INFORMATION_SCHEMA.KEY_COLUMN_USAGE AS k
                    JOIN %s.INFORMATION_SCHEMA.TABLE_CONSTRAINTS AS c
                        ON k.constraint_name = c.constraint_name
                        AND k.constraint_schema = c.constraint_schema
                        AND k.table_name = c.table_name
                    WHERE c.constraint_type = 'PRIMARY KEY'
                        AND k.table_name = %s
                    ORDER BY k.ordinal_position
                    """, datasetPath, datasetPath, quotedTableName);

            QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(primaryKeyQuery).build();
            TableResult result = this.getBigQueryClient().query(queryConfig);

            for (FieldValueList row : result.iterateAll()) {
                String columnName = row.get("column_name").getStringValue();
                primaryKeyColumns.add(columnName);
            }

            if (!primaryKeyColumns.isEmpty()) {
                logger.info("Retrieved {} primary key column(s) for table {}.{}",
                        primaryKeyColumns.size(), datasetName, tableName);
            }

        } catch (Exception e) {
            logger.warn("Failed to query primary key constraints for table {}.{}: {}",
                    datasetName, tableName, e.getMessage());
        }

        return primaryKeyColumns;
    }

    @Override
    public Map<String, Map<String, String>> getPropertiesForMerge(List<Resource> resources) {
        Map<String, Map<String, String>> result = new HashMap<>();
        try {
            String projectId = this.getConnectionDetails().get("project");
            Map<String, List<String>> allPrimaryKeyColumns = getAllPrimaryKeyColumns(projectId);

            for (Resource resource : resources) {
                String resourceName = resource.getResourceName();
                String datasetName = resource.getResourceProperties().get("dataset");
                String tableKey = datasetName + "." + resourceName;
                List<String> primaryKeyColumns = allPrimaryKeyColumns.getOrDefault(tableKey, new ArrayList<>());
                Map<String, String> mergeProperties = new HashMap<>();

                if (!primaryKeyColumns.isEmpty()) {
                    mergeProperties.put(BigQueryWriter.PRIMARY_KEY_COLUMNS, String.join(",", primaryKeyColumns));
                    mergeProperties.put(BigQueryWriter.MERGE, "true");

                } else {
                    mergeProperties.put(BigQueryWriter.MERGE, "false");
                    logger.info("No primary key columns found for table {}, merge disabled", tableKey);
                }

                result.put(resourceName, mergeProperties);
            }

            logger.info("Generated merge properties for {} resources", result.size());

        } catch (Exception e) {
            logger.error("Error occurred while generating merge properties: {}", e.getMessage(), e);
        }

        return result;
    }
}
