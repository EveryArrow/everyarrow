package com.everyarrow.dittoe.io.database.servers.mssql.writer.strategy;


import com.everyarrow.dittoe.io.data.DataStoreException;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.io.database.DatabaseUtils;
import com.everyarrow.dittoe.io.database.servers.jdbc.JDBCServer;
import com.everyarrow.dittoe.io.database.servers.jdbc.writer.strategy.JDBCWriteStrategy;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

import org.everyarrow.dittoe.schema.Field;
import org.everyarrow.dittoe.schema.SourceToTargetMapping;
import com.everyarrow.dittoe.utility.Batch;
import com.everyarrow.dittoe.utility.Chunk;
import com.everyarrow.dittoe.utility.metadata.MetadataRepositoryAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class MergeStrategy extends JDBCWriteStrategy {

    public static final String MERGE_MATCH_COLUMNS = "mergeMatchColumns";
    public static final String MERGE_COLUMNS_TO_IGNORE = "mergeColumnsToIgnore";
    // SQL Server has a limit of 2100 parameters per query
    // We need to calculate chunk size based on number of columns to stay under this limit
    private static final int SQL_SERVER_MAX_PARAMETERS = 2100;
    // Batch size for SQL Server (used for dividing data into chunks)
    private static final int SQL_SERVER_BATCH_SIZE = 10;
    private final Logger logger = LoggerFactory.getLogger(MergeStrategy.class);
    private int dynamicChunkSize = CHUNK_SIZE;

    public MergeStrategy() throws ExecutionException {
        super();
    }

    @Override
    public void init() throws ExecutionException {
        this.validateProperties();
        this.inferMergeMatchColumnsIfNeeded();
        super.init();
    }
    
    /**
     * Calculates the dynamic chunk size based on number of columns to avoid SQL Server's 2100 parameter limit.
     */
    private void calculateDynamicChunkSize() {
        // Calculate dynamic chunk size based on number of columns to avoid SQL Server's 2100 parameter limit
        int columnCount = MetadataRepositoryAPI.getPipelineMetadata().getS2TMap().size();
        if (columnCount > 0) {
            // Leave some buffer (use 2000 instead of 2100 for safety)
            this.dynamicChunkSize = Math.min(CHUNK_SIZE, (2000 / columnCount));
            // Ensure at least 1 row per chunk
            this.dynamicChunkSize = Math.max(1, this.dynamicChunkSize);
            logger.info("Calculated dynamic chunk size: {} (columns: {}, SQL Server param limit: {})", 
                        this.dynamicChunkSize, columnCount, SQL_SERVER_MAX_PARAMETERS);
        }
    }

    @Override
    public void write()
            throws ExecutionException {
        DataStoreInterface dataStore = DataStoreInterface.getDataStoreInterface();
        if (dataStore.size() == 0)
            return;

        long totalRowCount = dataStore.size();
        this.resetErrorRowCount();
        
        // Calculate dynamic chunk size based on column count (must be done here after metadata is loaded)
        this.calculateDynamicChunkSize();

        try {
            // Process batches sequentially to avoid deadlocks in SQL Server
            List<Batch> batches = Batch.divideIntoBatchesAndChunks(dataStore, this.dynamicChunkSize, SQL_SERVER_BATCH_SIZE);
            List<SourceToTargetMapping> sourceToTargetMappings = MetadataRepositoryAPI.getPipelineMetadata().getS2TMap();
            List<Field> targetFields = DatabaseUtils.getTargetFields(sourceToTargetMappings);

            for (int i = 0; i < batches.size(); i++) {
                int batchNumber = i + 1;
                Batch batch = batches.get(i);
                
                try (Connection connection = this.getWriter().getServer().getConnection()) {
                    connection.setAutoCommit(false);
                    this.executeBatchMerge(connection, dataStore, sourceToTargetMappings, batch, targetFields);
                } catch (Exception exception) {
                    if (this.shouldAutoCreateTargetTable(exception)) {
                        try (Connection connection = this.getWriter().getServer().getConnection()) {
                            connection.setAutoCommit(false);
                            connection.rollback();
                            this.getLogger().warn(
                                    "Target table missing for {}. Creating it and retrying merge batch.",
                                    this.getWriter().getServer().getSafeResourceName());
                            ((JDBCServer) this.getWriter().getServer()).createTargetTableIfNotExists(connection, targetFields);
                            connection.commit();
                            this.executeBatchMerge(connection, dataStore, sourceToTargetMappings, batch, targetFields);
                            continue;
                        } catch (Exception retryException) {
                            exception = retryException;
                        }
                    }
                    this.getLogger().error("Error in batch ({}/{}): {}", batchNumber, batches.size(), exception.getMessage());
                    this.handleBatchFailure(dataStore, sourceToTargetMappings, batch, targetFields, batchNumber, batches.size());
                }
            }

            // Notify observers with only successfully written rows (total - errors)
            long successfulRowCount = totalRowCount - this.getErrorRowCount();
            this.getWriter().notifyDataInsertionObservers(successfulRowCount);
        } catch (Exception e) {
            throw new ExecutionException("Could not write data", e);
        }
    }


    public void handleBatchFailure(DataStoreInterface dataStore, List<SourceToTargetMapping> sourceToTargetMappings,
                                   Batch batch, List<Field> targetFields, int batchNumber, int totalBatches) throws DataStoreException, IOException {
        int currentRetrySequenceIndex = 0;
        int retryStartIndex = batch.getChunks().getFirst().startIndex();
        int retryEndIndex = batch.getChunks().getLast().endIndex();
        int currentRetryStartIndex = retryStartIndex;

        while (true) {
            int currentRetrySequence = RETRY_SEQUENCE[currentRetrySequenceIndex];
            int currentRetryEndIndex = Math.min(currentRetryStartIndex + currentRetrySequence - 1, retryEndIndex);
            Batch currentBatch = Batch.divideIntoChunks(dataStore, currentRetryStartIndex, currentRetryEndIndex);

            try (Connection connection = this.getWriter().getServer().getConnection()) {
                connection.setAutoCommit(false);
                this.executeBatchMerge(connection, dataStore, sourceToTargetMappings, currentBatch, targetFields);

                if (currentRetryStartIndex == retryEndIndex || currentRetryEndIndex == retryEndIndex) {
                    // all batches have been retried and sequence end has been reached
                    break;
                }

                currentRetryStartIndex = currentRetryEndIndex + 1;
                currentRetrySequenceIndex = Math.min(currentRetrySequenceIndex + 1, RETRY_SEQUENCE.length - 1);
            } catch (Exception exception) {
                if (this.shouldAutoCreateTargetTable(exception)) {
                    try (Connection connection = this.getWriter().getServer().getConnection()) {
                        connection.setAutoCommit(false);
                        connection.rollback();
                        this.getLogger().warn(
                                "Target table missing for {}. Creating it and retrying merge batch.",
                                this.getWriter().getServer().getSafeResourceName());
                        ((JDBCServer) this.getWriter().getServer()).createTargetTableIfNotExists(connection, targetFields);
                        connection.commit();
                        this.executeBatchMerge(connection, dataStore, sourceToTargetMappings, currentBatch, targetFields);

                        if (currentRetryStartIndex == retryEndIndex || currentRetryEndIndex == retryEndIndex) {
                            break;
                        }

                        currentRetryStartIndex = currentRetryEndIndex + 1;
                        currentRetrySequenceIndex = Math.min(currentRetrySequenceIndex + 1, RETRY_SEQUENCE.length - 1);
                        continue;
                    } catch (Exception retryException) {
                        exception = retryException;
                    }
                }

                if (currentRetrySequenceIndex == 0) {
                    // found the erroneous row
                    this.writeErrorRow(sourceToTargetMappings, dataStore.get(currentRetryStartIndex), exception.getMessage());
                    currentRetryStartIndex += 1;
                } else {
                    currentRetrySequenceIndex = Math.max(currentRetrySequenceIndex - 1, 0);
                }
            }
        }
    }

    private void executeBatchMerge(Connection connection, DataStoreInterface dataStore,
                                   List<SourceToTargetMapping> sourceToTargetMappings,
                                   Batch batch, List<Field> targetFields) throws Exception {
        List<MergeStatementBuilder> queries = this.getMergeQueries(targetFields, batch);

        // If all rows were invalid, then we need to skip this chunk
        if (queries.isEmpty())
            return;

        // Execute for 0...n-1 (same number of rows in multiple merge statements)
        PreparedStatement statement = connection.prepareStatement(queries.getFirst().build());

        for (int i = 0; i < queries.size() - 1; ++i) {
            this.getWriter().setValues(statement, sourceToTargetMappings, dataStore, queries.get(i).getRows());
            statement.execute();
            statement.clearParameters();
        }

        if (queries.size() != 1) {
            statement.close();
            statement = connection.prepareStatement(queries.getLast().build());
        }

        // Execute for nth index (different number of rows in merge statement)
        this.getWriter().setValues(statement, sourceToTargetMappings, dataStore, queries.getLast().getRows());

        statement.execute();
        statement.close();
        connection.commit();
    }

    /**
     * Creates merge queries based on input parameters.
     * For the result, this function guarantees size for number of rows in 0...n-1 indices = chunkSize.
     * For the nth index, the size will be number of rows <= chunkSize
     *
     * @param columns Field objects of the table's columns.
     * @param batch   List of batches containing list of chunks indices.
     * @return List of MergeStatementBuilder objects.
     */
    private List<MergeStatementBuilder> getMergeQueries(List<Field> columns, Batch batch) {
        List<MergeStatementBuilder> mergeStatementBuilders = new LinkedList<>();

        MergeStatementBuilder mergeStatementBuilder = new MergeStatementBuilder(columns);
        for (Chunk chunk : batch.getChunks()) {
            for (int i = chunk.startIndex(); i <= chunk.endIndex(); ++i) {
                if (mergeStatementBuilder.getRows().size() == this.dynamicChunkSize) {
                    mergeStatementBuilders.add(mergeStatementBuilder);
                    mergeStatementBuilder = new MergeStatementBuilder(mergeStatementBuilder);
                    mergeStatementBuilder.getRows().clear();
                }

                mergeStatementBuilder.getRows().add(i);
            }
        }

        if (!mergeStatementBuilder.getRows().isEmpty()) {
            mergeStatementBuilders.add(mergeStatementBuilder);
        }

        // Since we have a guarantee that 0...n-1 will always be the same merge statement, we do not need to
        // re-generate these statements. Therefore, we will use the same statement for 0...n-1 and a new one for n
        return mergeStatementBuilders;
    }

    private Logger getLogger() {
        return logger;
    }

    private boolean shouldAutoCreateTargetTable(Throwable throwable) {
        if (!(this.getWriter().getServer() instanceof JDBCServer jdbcServer) || !jdbcServer.supportsTargetTableAutoCreate()) {
            return false;
        }

        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException && this.isMissingTableException(sqlException)) {
                return true;
            }
            current = current.getCause();
        }

        return false;
    }

    private boolean isMissingTableException(SQLException sqlException) {
        String message = sqlException.getMessage();
        if (message == null) {
            return false;
        }

        return message.contains("Invalid object name");
    }

    private void validateProperties() throws ExecutionException {
        Map<String, String> properties = this.getWriter().getServer().getResource().getResourceProperties();
        if (properties.get(MERGE_MATCH_COLUMNS) == null) {
            logger.info("Merge match columns not specified, will be inferred from table metadata");
        }
    }

    /**
     * Automatically infers merge match columns by querying SQL Server metadata for primary keys and unique constraints.
     * Uses SQL Server system views to find the best columns for matching records.
     */
    private void inferMergeMatchColumnsIfNeeded() throws ExecutionException {
        Map<String, String> properties = this.getWriter().getServer().getResource().getResourceProperties();

        if (properties.get(MERGE_MATCH_COLUMNS) != null) {
            logger.debug("Merge match columns already specified: {}", properties.get(MERGE_MATCH_COLUMNS));
            return;
        }

        try (Connection connection = this.getWriter().getServer().getConnection()) {
            String schema = this.getWriter().getServer().getResource().getResourceProperties().get("schema");
            String tableName = this.getWriter().getServer().getResource().getResourceName();

            List<String> matchColumns = inferMatchColumnsFromMetadata(connection, schema, tableName);

            if (!matchColumns.isEmpty()) {
                String matchColumnsStr = String.join(",", matchColumns);
                properties.put(MERGE_MATCH_COLUMNS, matchColumnsStr);
                logger.info("Automatically inferred merge match columns for table {}.{}: {}",
                        schema, tableName, matchColumnsStr);
            } else {
                // Fallback: Use all non-nullable columns as match columns if available
                List<String> fallbackColumns = getNonNullableColumns(connection, schema, tableName);
                if (!fallbackColumns.isEmpty()) {
                    String fallbackColumnsStr = String.join(",", fallbackColumns);
                    properties.put(MERGE_MATCH_COLUMNS, fallbackColumnsStr);
                    logger.warn("No primary key or unique constraints found for table {}.{}. " +
                                    "Using all non-nullable columns as fallback match columns: {}",
                            schema, tableName, fallbackColumnsStr);
                } else {
                    throw new ExecutionException("Could not infer merge match columns for table " + schema + "." + tableName +
                            ". No primary key, unique constraints, or suitable fallback columns found. " +
                            "Please specify " + MERGE_MATCH_COLUMNS + " property manually.");
                }
            }
        } catch (SQLException e) {
            throw new ExecutionException("Failed to infer merge match columns from database metadata", e);
        }
    }

    /**
     * Queries SQL Server system views to find primary key and unique constraint columns.
     * Priority order: Primary Key > Unique Constraints > Unique Indexes
     */
    private List<String> inferMatchColumnsFromMetadata(Connection connection, String schema, String tableName) throws SQLException {
        // First, try to find primary key columns
        List<String> primaryKeyColumns = getPrimaryKeyColumns(connection, schema, tableName);
        if (!primaryKeyColumns.isEmpty()) {
            logger.debug("Found primary key columns for {}.{}: {}", schema, tableName, primaryKeyColumns);
            return primaryKeyColumns;
        }

        // If no primary key, look for unique constraints
        List<String> uniqueConstraintColumns = getUniqueConstraintColumns(connection, schema, tableName);
        if (!uniqueConstraintColumns.isEmpty()) {
            logger.debug("Found unique constraint columns for {}.{}: {}", schema, tableName, uniqueConstraintColumns);
            return uniqueConstraintColumns;
        }

        // If no unique constraints, look for unique indexes
        List<String> uniqueIndexColumns = getUniqueIndexColumns(connection, schema, tableName);
        if (!uniqueIndexColumns.isEmpty()) {
            logger.debug("Found unique index columns for {}.{}: {}", schema, tableName, uniqueIndexColumns);
            return uniqueIndexColumns;
        }

        return List.of(); // No suitable columns found
    }

    /**
     * Gets primary key columns using SQL Server system views.
     */
    private List<String> getPrimaryKeyColumns(Connection connection, String schema, String tableName) throws SQLException {
        String query = """
                SELECT c.name AS column_name
                FROM sys.schemas s
                JOIN sys.tables t ON s.schema_id = t.schema_id
                JOIN sys.indexes i ON t.object_id = i.object_id
                JOIN sys.index_columns ic ON i.object_id = ic.object_id AND i.index_id = ic.index_id
                JOIN sys.columns c ON ic.object_id = c.object_id AND ic.column_id = c.column_id
                WHERE s.name = ? AND t.name = ? AND i.is_primary_key = 1
                ORDER BY ic.key_ordinal
                """;

        List<String> columns = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, schema);
            stmt.setString(2, tableName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    columns.add(rs.getString("column_name"));
                }
            }
        }
        return columns;
    }

    /**
     * Gets non-nullable columns as a fallback when no primary key or unique constraints exist.
     * This provides a reasonable fallback for merge operations.
     */
    private List<String> getNonNullableColumns(Connection connection, String schema, String tableName) throws SQLException {
        String query = """
                SELECT c.name AS column_name
                FROM sys.schemas s
                JOIN sys.tables t ON s.schema_id = t.schema_id
                JOIN sys.columns c ON t.object_id = c.object_id
                WHERE s.name = ? AND t.name = ? AND c.is_nullable = 0
                ORDER BY c.column_id
                """;

        List<String> columns = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, schema);
            stmt.setString(2, tableName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    columns.add(rs.getString("column_name"));
                }
            }
        }
        return columns;
    }

    /**
     * Gets unique constraint columns using SQL Server system views.
     */
    private List<String> getUniqueConstraintColumns(Connection connection, String schema, String tableName) throws SQLException {
        String query = """
                SELECT c.name AS column_name
                FROM sys.schemas s
                JOIN sys.tables t ON s.schema_id = t.schema_id
                JOIN sys.key_constraints kc ON t.object_id = kc.parent_object_id
                JOIN sys.indexes i ON kc.parent_object_id = i.object_id AND kc.unique_index_id = i.index_id
                JOIN sys.index_columns ic ON i.object_id = ic.object_id AND i.index_id = ic.index_id
                JOIN sys.columns c ON ic.object_id = c.object_id AND ic.column_id = c.column_id
                WHERE s.name = ? AND t.name = ? AND kc.type = 'UQ'
                ORDER BY ic.key_ordinal
                """;

        List<String> columns = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, schema);
            stmt.setString(2, tableName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    columns.add(rs.getString("column_name"));
                }
            }
        }
        return columns;
    }

    /**
     * Gets unique index columns using SQL Server system views.
     */
    private List<String> getUniqueIndexColumns(Connection connection, String schema, String tableName) throws SQLException {
        String query = """
                SELECT c.name AS column_name
                FROM sys.schemas s
                JOIN sys.tables t ON s.schema_id = t.schema_id
                JOIN sys.indexes i ON t.object_id = i.object_id
                JOIN sys.index_columns ic ON i.object_id = ic.object_id AND i.index_id = ic.index_id
                JOIN sys.columns c ON ic.object_id = c.object_id AND ic.column_id = c.column_id
                WHERE s.name = ? AND t.name = ? 
                  AND i.is_unique = 1 
                  AND i.is_primary_key = 0
                  AND i.type IN (1, 2)  -- Clustered or Nonclustered
                ORDER BY ic.key_ordinal
                """;

        List<String> columns = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, schema);
            stmt.setString(2, tableName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    columns.add(rs.getString("column_name"));
                }
            }
        }
        return columns;
    }

    private class MergeStatementBuilder {
        private final StringBuilder builder;
        private final StringBuilder rowTemplate;
        private final List<Field> columns;
        private List<Integer> rows = new LinkedList<>();

        public MergeStatementBuilder(List<Field> columns) {
            this.columns = columns;

            // Build the MERGE statement structure
            this.builder = new StringBuilder("MERGE ")
                    .append(MergeStrategy.this.getWriter().getServer().getSafeResourceName())
                    .append(" AS target USING (VALUES ");

            this.rowTemplate = new StringBuilder()
                    .append("( ");

            for (int j = 0; j < columns.size(); j++) {
                if (j != 0)
                    this.rowTemplate.append(", ");

                this.rowTemplate
                        .append(MergeStrategy.this.getWriter().getServer().getDataTypeCast("?", columns.get(j).getDataType()));
            }

            this.rowTemplate.append(" )");
        }

        public MergeStatementBuilder(MergeStatementBuilder mergeStatementBuilder) {
            this.builder = new StringBuilder(mergeStatementBuilder.builder);
            this.rowTemplate = new StringBuilder(mergeStatementBuilder.rowTemplate);
            this.rows = new LinkedList<>(mergeStatementBuilder.rows);
            this.columns = mergeStatementBuilder.columns;
        }

        public List<Integer> getRows() {
            return rows;
        }

        public String build() {
            if (this.rows.isEmpty())
                return null;

            StringBuilder queryBuilder = new StringBuilder(this.builder);

            // Add VALUES clause with row templates
            for (int i = 0; i < rows.size(); ++i) {
                if (i != 0)
                    queryBuilder.append(", ");

                queryBuilder.append(this.rowTemplate);
            }

            // Complete the VALUES clause with column aliases
            queryBuilder.append(") AS source (");
            boolean firstColumn = true;
            for (Field column : this.columns) {
                if (!firstColumn) {
                    queryBuilder.append(", ");
                } else {
                    firstColumn = false;
                }
                queryBuilder.append(MergeStrategy.this.getWriter().getServer().escapeIdentifierName(column.getName()));
            }
            queryBuilder.append(") ");

            // Add ON clause for matching
            String matchColumns = MergeStrategy.this.getWriter().getServer().getResource().getResourceProperties().get(MERGE_MATCH_COLUMNS);
            String[] matchColumnArray = matchColumns.split(",");

            queryBuilder.append("ON ");
            for (int i = 0; i < matchColumnArray.length; i++) {
                if (i > 0) {
                    queryBuilder.append(" AND ");
                }
                String escapedColumnName = MergeStrategy.this.getWriter().getServer().escapeIdentifierName(matchColumnArray[i].trim());
                queryBuilder.append("target.").append(escapedColumnName)
                        .append(" = source.").append(escapedColumnName);
            }

            // Add WHEN MATCHED clause for UPDATE
            queryBuilder.append(" WHEN MATCHED THEN UPDATE SET ");

                Set<String> configuredColumnsToIgnore =
                    MergeStrategy.this.getWriter().getServer().getResource().getResourceProperties().containsKey(MERGE_COLUMNS_TO_IGNORE) ?
                            Arrays.stream(MergeStrategy.this.getWriter().getServer().getResource().getResourceProperties().get(MERGE_COLUMNS_TO_IGNORE).split(","))
                                    .map(String::trim).collect(Collectors.toSet()) : new HashSet<>();

                Set<String> updateColumnsToIgnore = new HashSet<>(configuredColumnsToIgnore);
                Collections.addAll(updateColumnsToIgnore, matchColumnArray);

            int written = 0;
            for (Field column : this.columns) {
                if (updateColumnsToIgnore.contains(column.getName().trim())) {
                    continue;
                }

                if (written++ != 0) {
                    queryBuilder.append(", ");
                }

                String escapedColumnName = MergeStrategy.this.getWriter().getServer().escapeIdentifierName(column.getName());
                queryBuilder.append(escapedColumnName)
                        .append(" = source.")
                        .append(escapedColumnName);
            }

            // Add WHEN NOT MATCHED clause for INSERT
            queryBuilder.append(" WHEN NOT MATCHED THEN INSERT (");

            firstColumn = true;
            for (Field column : this.columns) {
                if (configuredColumnsToIgnore.contains(column.getName().trim())) {
                    continue;
                }
                if (!firstColumn) {
                    queryBuilder.append(", ");
                } else {
                    firstColumn = false;
                }
                queryBuilder.append(MergeStrategy.this.getWriter().getServer().escapeIdentifierName(column.getName()));
            }

            queryBuilder.append(") VALUES (");

            firstColumn = true;
            for (Field column : this.columns) {
                if (configuredColumnsToIgnore.contains(column.getName().trim())) {
                    continue;
                }
                if (!firstColumn) {
                    queryBuilder.append(", ");
                } else {
                    firstColumn = false;
                }
                queryBuilder.append("source.").append(MergeStrategy.this.getWriter().getServer().escapeIdentifierName(column.getName()));
            }

            queryBuilder.append(");");

            return queryBuilder.toString();
        }
    }
}
