package com.everyarrow.dittoe.io.api.servers.bigquery;

import org.everyarrow.dittoe.schema.DataType;
import com.google.cloud.bigquery.*;
import org.everyarrow.dittoe.schema.Field;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

import static com.google.cloud.bigquery.Field.Mode;
import static com.google.cloud.bigquery.Field.newBuilder;

/**
 * Handles schema management operations for BigQuery tables.
 * This class provides functionality to detect schema changes and recreate tables
 * when the schema differs from the expected resource schema.
 */
public class BigQuerySchemaManager {
    private static final Logger logger = LoggerFactory.getLogger(BigQuerySchemaManager.class);
    private final BigQueryServer bigQueryServer;
    private final BigQueryTableMerger tableMerger;

    public BigQuerySchemaManager(BigQueryServer bigQueryServer, BigQueryTableMerger tableMerger) {
        this.bigQueryServer = bigQueryServer;
        this.tableMerger = tableMerger;
    }

    /**
     * Checks if the table schema has changed and recreates the table if necessary.
     * Compares the existing table schema with the expected resource schema.
     *
     * @param tableName The name of the table to check
     * @param expectedFields The expected schema fields
     */
    public void recreateTableOnSchemaChange(String tableName, List<Field> expectedFields) {
        logger.info("Checking schema changes for table: {}", tableName);
        
        TableId tableId = createTableId(tableName);
        Table table = bigQueryServer.getBigQueryClient().getTable(tableId);

        if (table == null) {
            logger.info("Table {} does not exist, no schema change check needed", tableName);
            return;
        }

        boolean recreate = shouldRecreateTable(table, expectedFields);

        if (recreate) {
            logger.info("Recreating table {} because schema changed", tableId.getIAMResourceName());
            recreateTable(tableName, expectedFields);
            logger.info("Successfully recreated table {} with new schema", tableName);
        } else {
            logger.info("Table {} schema is up to date, no recreation needed", tableName);
        }
    }

    /**
     * Determines if a table should be recreated based on schema differences.
     *
     * @param existingTable The existing BigQuery table
     * @param expectedFields The expected schema fields
     * @return true if the table should be recreated, false otherwise
     */
    private boolean shouldRecreateTable(Table existingTable, List<Field> expectedFields) {
        Schema existingSchema = existingTable.getDefinition().getSchema();
        if (existingSchema == null) {
            logger.warn("Existing table has no schema, recreation required");
            return true;
        }

        List<com.google.cloud.bigquery.Field> existingFields = existingSchema.getFields().stream().toList();
        List<com.google.cloud.bigquery.Field> expectedBigQueryFields = convertToBigQueryFields(expectedFields);

        // Check if field count differs
        if (existingFields.size() != expectedBigQueryFields.size()) {
            logger.info("Field count differs: existing={}, expected={}", 
                       existingFields.size(), expectedBigQueryFields.size());
            return true;
        }

        // Check if all expected fields exist with correct types
        for (com.google.cloud.bigquery.Field expectedField : expectedBigQueryFields) {
            if (!fieldExistsWithCorrectType(existingFields, expectedField)) {
                logger.info("Field mismatch detected: expected field '{}' with type '{}' not found or has different type",
                           expectedField.getName(), expectedField.getType().getStandardType());
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if a field exists in the existing fields list with the correct name and type.
     *
     * @param existingFields List of existing BigQuery fields
     * @param expectedField The expected field to find
     * @return true if the field exists with correct type, false otherwise
     */
    private boolean fieldExistsWithCorrectType(List<com.google.cloud.bigquery.Field> existingFields, 
                                              com.google.cloud.bigquery.Field expectedField) {
        return existingFields.stream()
                .anyMatch(existingField -> 
                    existingField.getName().equals(expectedField.getName()) &&
                    existingField.getType().getStandardType() == expectedField.getType().getStandardType());
    }

    /**
     * Converts a list of Field objects to BigQuery Field objects.
     *
     * @param fields List of Field objects
     * @return List of BigQuery Field objects
     */
    private List<com.google.cloud.bigquery.Field> convertToBigQueryFields(List<Field> fields) {
        return fields.stream()
                .map(field -> newBuilder(field.getName(),
                        BigQueryServer.dataTypeToStandardSQLType(field.getDataType()))
                        .setMode(field.isNullable() ? Mode.NULLABLE : Mode.REQUIRED)
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Recreates a table by dropping it and creating it with the new schema.
     *
     * @param tableName The name of the table to recreate
     * @param newFields The new schema fields
     */
    private void recreateTable(String tableName, List<Field> newFields) {
        // Delete the existing table
        tableMerger.deleteTable(tableName);
        
        // Create the table with new schema
        tableMerger.createTargetTableIfNotExists(tableName, newFields);
    }

    /**
     * Creates a TableId from the table name using the server's connection details.
     *
     * @param tableName The name of the table
     * @return TableId object
     */
    private TableId createTableId(String tableName) {
        String projectId = bigQueryServer.getConnectionDetails().get("project");
        String datasetName = bigQueryServer.getResource().getResourceProperties().get("dataset");
        return TableId.of(projectId, datasetName, tableName);
    }

    /**
     * Gets the current schema of a table.
     *
     * @param tableName The name of the table
     * @return List of Field objects representing the current schema, or empty list if table doesn't exist
     */
    public List<Field> getCurrentTableSchema(String tableName) {
        TableId tableId = createTableId(tableName);
        Table table = bigQueryServer.getBigQueryClient().getTable(tableId);
        
        if (table == null) {
            logger.warn("Table {} does not exist", tableName);
            return List.of();
        }
        
        Schema schema = table.getDefinition().getSchema();
        if (schema == null) {
            logger.warn("Schema not found for table: {}", tableName);
            return List.of();
        }
        
        return schema.getFields().stream()
                .map(field -> {
                    boolean isNullable = field.getMode() == null || 
                                       field.getMode().equals(Mode.NULLABLE);
                    return new Field(field.getName(), 
                                   BigQueryServer.standardSQLTypeToDataType(field.getType().getStandardType()), 
                                   isNullable);
                })
                .collect(Collectors.toList());
    }

    /**
     * Compares two schemas and returns a detailed comparison result.
     *
     * @param currentFields Current table schema
     * @param expectedFields Expected schema
     * @return SchemaComparisonResult containing details about differences
     */
    public SchemaComparisonResult compareSchemas(List<Field> currentFields, List<Field> expectedFields) {
        SchemaComparisonResult result = new SchemaComparisonResult();
        
        // Find missing fields (in expected but not in current)
        List<String> currentFieldNames = currentFields.stream()
                .map(Field::getName)
                .collect(Collectors.toList());
        
        result.missingFields = expectedFields.stream()
                .filter(field -> !currentFieldNames.contains(field.getName()))
                .collect(Collectors.toList());
        
        // Find extra fields (in current but not in expected)
        List<String> expectedFieldNames = expectedFields.stream()
                .map(Field::getName)
                .collect(Collectors.toList());
        
        result.extraFields = currentFields.stream()
                .filter(field -> !expectedFieldNames.contains(field.getName()))
                .collect(Collectors.toList());
        
        // Find type mismatches
        for (Field expectedField : expectedFields) {
            currentFields.stream()
                    .filter(currentField -> currentField.getName().equals(expectedField.getName()))
                    .findFirst()
                    .ifPresent(currentField -> {
                        if (!currentField.getDataType().equals(expectedField.getDataType())) {
                            result.typeMismatches.add(new FieldTypeMismatch(
                                expectedField.getName(),
                                currentField.getDataType(),
                                expectedField.getDataType()
                            ));
                        }
                    });
        }
        
        result.schemasMatch = result.missingFields.isEmpty() && 
                             result.extraFields.isEmpty() && 
                             result.typeMismatches.isEmpty();
        
        return result;
    }

    /**
     * Result of schema comparison operation.
     */
    public static class SchemaComparisonResult {
        public boolean schemasMatch;
        public List<Field> missingFields = List.of();
        public List<Field> extraFields = List.of();
        public List<FieldTypeMismatch> typeMismatches = List.of();
    }

    /**
     * Represents a field type mismatch between current and expected schemas.
     */
    public static class FieldTypeMismatch {
        public final String fieldName;
        public final DataType currentType;
        public final DataType expectedType;

        public FieldTypeMismatch(String fieldName, 
                               DataType currentType,
                               DataType expectedType) {
            this.fieldName = fieldName;
            this.currentType = currentType;
            this.expectedType = expectedType;
        }
    }
}
