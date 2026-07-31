package com.everyarrow.dittoe.io.api.servers.bigquery;

import com.everyarrow.dittoe.utility.security.SqlSafety;
import com.google.cloud.bigquery.QueryJobConfiguration;
import org.everyarrow.dittoe.schema.Field;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles table merge operations for BigQuery.
 * This class provides functionality to merge data from a temporary table
 * into a target table using MERGE statements with primary key matching.
 */
public class BigQueryTableMerger {
    static final String MERGE_SOURCE_ROW_NUMBER_ALIAS = "_dittoe_row_num";
    private static final Logger logger = LoggerFactory.getLogger(BigQueryTableMerger.class);
    private final BigQueryServer bigQueryServer;

    public BigQueryTableMerger(BigQueryServer bigQueryServer) {
        this.bigQueryServer = bigQueryServer;
    }

    /**
     * Merges data from a temporary table into the target table.
     * Uses MERGE statement with primary key matching for upsert operations.
     *
     * @param targetTableName The name of the target table to merge into
     * @param temporaryTableName The name of the temporary table containing new data
     * @param primaryKeyColumns Comma-separated string of primary key column names
     * @param fields List of all fields in the table schema
     * @throws InterruptedException if the operation is interrupted
     */
    public void mergeTable(String targetTableName, String temporaryTableName, String primaryKeyColumns, List<Field> fields) throws InterruptedException {
        logger.info("Merging {} into {}",
                getTableFullName(temporaryTableName),
                getTableFullName(targetTableName)
        );

        String mergeQuery = buildMergeQuery(targetTableName, temporaryTableName, primaryKeyColumns, fields);

        logger.debug("Executing merge query: {}", mergeQuery);

        bigQueryServer.getBigQueryClient().query(QueryJobConfiguration.of(mergeQuery));

        logger.info("Successfully merged {} into {}",
                getTableFullName(temporaryTableName),
                getTableFullName(targetTableName)
        );
    }

    /**
     * Builds the MERGE SQL statement for BigQuery.
     *
     * @param targetTableName The target table name
     * @param temporaryTableName The temporary table name
     * @param primaryKeyColumns Comma-separated primary key columns
     * @param fields List of all table fields
     * @return Complete MERGE SQL statement
     */
    private String buildMergeQuery(String targetTableName, String temporaryTableName, String primaryKeyColumns, List<Field> fields) {
        String targetTable = getTableFullName(targetTableName);
        String tempTable = getTableFullName(temporaryTableName);
        List<String> mergeKeyColumns = parsePrimaryKeyColumns(primaryKeyColumns);

        // Build the ON condition using primary key columns
        String onCondition = mergeKeyColumns.stream()
                .map(key -> String.format("o.%s = t.%s", escapeColumnName(key), escapeColumnName(key)))
                .collect(Collectors.joining(" AND "));

        String mergeSource = buildMergeSourceExpression(tempTable, mergeKeyColumns);

        // Build the column list for INSERT
        String insertColumns = fields.stream()
                .map(field -> escapeColumnName(field.getName()))
                .collect(Collectors.joining(", "));

        // Build the VALUES clause for INSERT
        String insertValues = fields.stream()
                .map(field -> String.format("t.%s", escapeColumnName(field.getName())))
                .collect(Collectors.joining(", "));

        // Build the SET clause for UPDATE
        String updateSet = fields.stream()
                .map(field -> String.format("%s = t.%s",
                    escapeColumnName(field.getName()),
                    escapeColumnName(field.getName())))
                .collect(Collectors.joining(", "));

        return String.format("""
            MERGE INTO %s AS o
            USING (
                %s
            ) AS t
            ON %s
            WHEN NOT MATCHED BY TARGET THEN
                INSERT (%s)
                VALUES (%s)
            WHEN MATCHED THEN
                UPDATE SET %s
            """, targetTable, mergeSource, onCondition, insertColumns, insertValues, updateSet);
    }

    static String buildMergeSourceExpression(String tempTable, List<String> mergeKeyColumns) {
        String partitionBy = mergeKeyColumns.stream()
                .map(BigQueryTableMerger::escapeColumnNameStatic)
                .collect(Collectors.joining(", "));

        return String.format("""
            SELECT * EXCEPT (%s)
            FROM (
                SELECT source.*,
                       ROW_NUMBER() OVER (PARTITION BY %s ORDER BY TO_JSON_STRING(source)) AS %s
                FROM %s AS source
            )
            WHERE %s = 1
            """,
                escapeColumnNameStatic(MERGE_SOURCE_ROW_NUMBER_ALIAS),
                partitionBy,
                escapeColumnNameStatic(MERGE_SOURCE_ROW_NUMBER_ALIAS),
                tempTable,
                escapeColumnNameStatic(MERGE_SOURCE_ROW_NUMBER_ALIAS));
    }

    private static List<String> parsePrimaryKeyColumns(String primaryKeyColumns) {
        return Arrays.stream(primaryKeyColumns.split(","))
                .map(String::trim)
                .filter(column -> !column.isEmpty())
                .toList();
    }

    /**
     * Creates a full table name in the format project.dataset.table.
     *
     * @param tableName The table name
     * @return Full table name with project and dataset
     */
    private String getTableFullName(String tableName) {
        String projectId = bigQueryServer.getConnectionDetails().get("project");
        String datasetName = bigQueryServer.getResource().getResourceProperties().get("dataset");
        return SqlSafety.quoteBigQueryPath(projectId, datasetName, tableName);
    }

    /**
     * Escapes identifier names (table, dataset, project names) with backticks.
     *
     * @param identifierName The identifier to escape
     * @return Escaped identifier name
     */
    private String escapeIdentifierName(String identifierName) {
        return SqlSafety.quoteBigQueryIdentifier(identifierName);
    }

    /**
     * Escapes column names for use in SQL queries.
     *
     * @param columnName The column name to escape
     * @return Escaped column name
     */
    private String escapeColumnName(String columnName) {
        return SqlSafety.quoteBigQueryIdentifier(columnName);
    }

    private static String escapeColumnNameStatic(String columnName) {
        return SqlSafety.quoteBigQueryIdentifier(columnName);
    }

    /**
     * Validates that primary key columns exist in the provided fields.
     *
     * @param primaryKeyColumns Comma-separated primary key column names
     * @param fields List of table fields
     * @return true if all primary key columns exist, false otherwise
     */
    public boolean validatePrimaryKeyColumns(String primaryKeyColumns, List<Field> fields) {
        if (primaryKeyColumns == null || primaryKeyColumns.trim().isEmpty()) {
            logger.warn("Primary key columns not specified for merge operation");
            return false;
        }

        List<String> fieldNames = fields.stream()
                .map(Field::getName)
                .collect(Collectors.toList());

        for (String pkColumn : parsePrimaryKeyColumns(primaryKeyColumns)) {
            if (!fieldNames.contains(pkColumn)) {
                logger.error("Primary key column '{}' not found in table schema", pkColumn);
                return false;
            }
        }

        logger.info("Primary key validation successful for columns: {}", primaryKeyColumns);
        return true;
    }

    /**
     * Checks if the target table exists.
     *
     * @param tableName The name of the table to check
     * @return true if the table exists, false otherwise
     */
    public boolean targetTableExists(String tableName) {
        String projectId = bigQueryServer.getConnectionDetails().get("project");
        String datasetName = bigQueryServer.getResource().getResourceProperties().get("dataset");

        com.google.cloud.bigquery.TableId tableId = com.google.cloud.bigquery.TableId.of(
            projectId, datasetName, tableName);

        com.google.cloud.bigquery.Table table = bigQueryServer.getBigQueryClient().getTable(tableId);
        return table != null;
    }

    /**
     * Creates the target table if it doesn't exist.
     *
     * @param tableName The name of the table to create
     * @param fields The schema fields for the table
     */
    public void createTargetTableIfNotExists(String tableName, List<Field> fields) {
        if (targetTableExists(tableName)) {
            logger.info("Target table {} already exists", tableName);
            return;
        }

        logger.info("Creating target table: {}", tableName);

        String projectId = bigQueryServer.getConnectionDetails().get("project");
        String datasetName = bigQueryServer.getResource().getResourceProperties().get("dataset");

        com.google.cloud.bigquery.TableId tableId = com.google.cloud.bigquery.TableId.of(
            projectId, datasetName, tableName);

        com.google.cloud.bigquery.Schema schema = com.google.cloud.bigquery.Schema.of(
            fields.stream().map(field ->
                com.google.cloud.bigquery.Field.newBuilder(
                    field.getName(),
                    BigQueryServer.dataTypeToStandardSQLType(field.getDataType()))
                    .setMode(field.isNullable() ?
                        com.google.cloud.bigquery.Field.Mode.NULLABLE :
                        com.google.cloud.bigquery.Field.Mode.REQUIRED)
                    .build())
                .collect(Collectors.toList()));

        com.google.cloud.bigquery.TableInfo tableInfo = com.google.cloud.bigquery.TableInfo.of(
            tableId, com.google.cloud.bigquery.StandardTableDefinition.of(schema));

        com.google.cloud.bigquery.Table createdTable = bigQueryServer.getBigQueryClient().create(tableInfo);

        logger.info("Successfully created target table: {}", createdTable.getTableId().getIAMResourceName());
    }

    /**
     * Deletes a table.
     *
     * @param tableName The name of the table to delete
     */
    public void deleteTable(String tableName) {
        String projectId = bigQueryServer.getConnectionDetails().get("project");
        String datasetName = bigQueryServer.getResource().getResourceProperties().get("dataset");

        com.google.cloud.bigquery.TableId tableId = com.google.cloud.bigquery.TableId.of(
            projectId, datasetName, tableName);

        logger.info("Deleting table: {}", getTableFullName(tableName));

        if (bigQueryServer.getBigQueryClient().delete(tableId)) {
            logger.info("Successfully deleted table: {}", getTableFullName(tableName));
        } else {
            logger.warn("Failed to delete table: {}", getTableFullName(tableName));
        }
    }
}
