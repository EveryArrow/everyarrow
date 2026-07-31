package com.everyarrow.dittoe.io.database.servers.jdbc.writer.strategy;

import com.everyarrow.dittoe.io.data.DataStoreException;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.io.database.DatabaseUtils;
import com.everyarrow.dittoe.io.database.servers.jdbc.JDBCServer;
import com.everyarrow.dittoe.pipelines.behaviors.instrumentation.Instrumentation;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.Field;
import org.everyarrow.dittoe.schema.PipelineMetadata;
import org.everyarrow.dittoe.schema.SourceToTargetMapping;
import com.everyarrow.dittoe.utility.Batch;
import com.everyarrow.dittoe.utility.Chunk;
import com.everyarrow.dittoe.utility.ExecutorService;
import com.everyarrow.dittoe.utility.metadata.MetadataRepositoryAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;

public class InsertStrategy extends JDBCWriteStrategy {
    private final Logger logger = LoggerFactory.getLogger(InsertStrategy.class);

    public InsertStrategy() throws ExecutionException {
        super();
    }

    /**
     * Creates and returns a list containing callables.
     * Each callable would insert a batch in the database.
     *
     * @param dataStore              DataStore object.
     * @param sourceToTargetMappings Mapping of source to target fields.
     * @param batches                Batches into which total rows are divided.
     * @return List of callables.
     */
    private List<Callable<Object>> getTasks(DataStoreInterface dataStore,
            List<SourceToTargetMapping> sourceToTargetMappings,
            List<Batch> batches) {
        final List<Field> targetFields = DatabaseUtils.getTargetFields(sourceToTargetMappings);
        final List<Callable<Object>> tasks = new LinkedList<>();

        DataStoreInterface dataStoreInterface = DataStoreInterface.getDataStoreInterface();
        PipelineMetadata threadLocalPipelineMetadata = MetadataRepositoryAPI.getPipelineMetadata();
        Instrumentation threadLocalInstrumentation = Instrumentation.getInstance();
        String pipelineMetadataRepositoryAPIKey = MetadataRepositoryAPI.getApiKey();
        String instrumentationAPIKey = Instrumentation.getApiKey();

        for (int i = 0; i < batches.size(); ++i) {
            int batchNumber = i + 1;
            Batch batch = batches.get(i);

            tasks.add(() -> {
                DataStoreInterface.setDataStoreInterface(dataStoreInterface);
                MetadataRepositoryAPI.setPipelineMetadata(threadLocalPipelineMetadata);
                MetadataRepositoryAPI.setApiKey(pipelineMetadataRepositoryAPIKey);
                Instrumentation.setInstance(threadLocalInstrumentation);
                Instrumentation.setApiKey(instrumentationAPIKey);

                try (Connection connection = this.getWriter().getServer().getConnection()) {
                    // auto commit set to false for performance gain
                    connection.setAutoCommit(false);
                    InsertStrategy.this.executeBatchInsertWithTargetAutoCreate(connection, dataStore,
                            sourceToTargetMappings, batch,
                            targetFields);
                } catch (Exception exception) {
                    InsertStrategy.this.getLogger().error(
                            "Error occurred in batch {}/{}; retrying batch. Cause type: {}; message: {}",
                            batchNumber, batches.size(), exception.getClass().getName(), exception.getMessage());
                    InsertStrategy.this.handleBatchFailure(dataStore, sourceToTargetMappings, batch, targetFields,
                            batchNumber, batches.size());
                }

                return null;
            });
        }

        return tasks;
    }

    public void handleBatchFailure(DataStoreInterface dataStore, List<SourceToTargetMapping> sourceToTargetMappings,
            Batch batch, List<Field> targetFields, int batchNumber, int totalBatches)
            throws DataStoreException, IOException {
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
                this.executeBatchInsertWithTargetAutoCreate(connection, dataStore, sourceToTargetMappings,
                        currentBatch, targetFields);

                if (currentRetryStartIndex == retryEndIndex || currentRetryEndIndex == retryEndIndex) {
                    // all batches have been retried and sequence end has been reached
                    break;
                }

                currentRetryStartIndex = currentRetryEndIndex + 1;
                currentRetrySequenceIndex = Math.min(currentRetrySequenceIndex + 1, RETRY_SEQUENCE.length - 1);
            } catch (Exception exception) {

                if (currentRetrySequenceIndex == 0) {
                    // found the erroneous row
                    this.writeErrorRow(sourceToTargetMappings, dataStore.get(currentRetryStartIndex),
                            exception.getMessage());
                    currentRetryStartIndex += 1;
                } else {
                    currentRetrySequenceIndex = Math.max(currentRetrySequenceIndex - 1, 0);
                }
            }
        }
    }

    private void executeBatchInsert(Connection connection, DataStoreInterface dataStore,
            List<SourceToTargetMapping> sourceToTargetMappings,
            Batch batch, List<Field> targetFields) throws Exception {
        List<InsertStatementBuilder> queries = this.getInsertQueries(targetFields, batch, dataStore);

        // If all rows were invalid, then we need to skip this chunk
        if (queries.isEmpty())
            return;

        // Execute for 0...n-1 (same number of rows in multiple insert statements)
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

        // Execute for nth index (different number of rows in insert statement)
        this.getWriter().setValues(statement, sourceToTargetMappings, dataStore, queries.getLast().getRows());
        statement.execute();
        statement.close();
        connection.commit();
    }

    private void executeBatchInsertWithTargetAutoCreate(Connection connection, DataStoreInterface dataStore,
            List<SourceToTargetMapping> sourceToTargetMappings,
            Batch batch, List<Field> targetFields) throws Exception {
        try {
            this.executeBatchInsert(connection, dataStore, sourceToTargetMappings, batch, targetFields);
        } catch (Exception exception) {
            if (!this.shouldAutoCreateTargetTable(exception)) {
                throw exception;
            }

            this.getLogger().warn("Target table missing for {}. Creating it and retrying batch.",
                    this.getWriter().getServer().getSafeResourceName());
            connection.rollback();
            this.getWriter().getServer().createTargetTableIfNotExists(connection, targetFields);
            connection.commit();
            this.executeBatchInsert(connection, dataStore, sourceToTargetMappings, batch, targetFields);
        }
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

    static boolean isMissingTableException(SQLException sqlException) {
        String sqlState = sqlException.getSQLState();
        int errorCode = sqlException.getErrorCode();
        String message = sqlException.getMessage();

        return "42S02".equalsIgnoreCase(sqlState)
                || "42P01".equalsIgnoreCase(sqlState)
                || errorCode == 1146
                || (message != null
                && (message.toLowerCase().contains("doesn't exist")
                || message.toLowerCase().contains("does not exist")
                || message.toLowerCase().contains("no such table")));
    }

    /**
     * Creates insert queries based on input parameters.
     * For the result, this function guarantees size for number of rows in 0...n-1
     * indices = chunkSize.
     * For the nth index, the size will be number of rows <= chunkSize
     *
     * @param columns Field objects of the table's columns.
     * @param batch   List of batches containing list of chunks indices.
     * @return List of InsertStatementBuilder objects.
     */
    private List<InsertStatementBuilder> getInsertQueries(List<Field> columns, Batch batch,
            DataStoreInterface dataStore) throws DataStoreException {
        List<InsertStatementBuilder> insertStatementBuilders = new LinkedList<>();

        InsertStatementBuilder insertStatementBuilder = new InsertStatementBuilder(columns);
        for (Chunk chunk : batch.getChunks()) {
            for (int i = chunk.startIndex(); i <= chunk.endIndex(); ++i) {
                if ((Boolean) dataStore.get(i).getOrDefault("alreadyExists", false))
                    continue;

                if (insertStatementBuilder.getRows().size() == CHUNK_SIZE) {
                    insertStatementBuilders.add(insertStatementBuilder);
                    insertStatementBuilder = new InsertStatementBuilder(insertStatementBuilder);
                    insertStatementBuilder.getRows().clear();
                }

                insertStatementBuilder.getRows().add(i);
            }
        }

        if (!insertStatementBuilder.getRows().isEmpty()) {
            insertStatementBuilders.add(insertStatementBuilder);
        }

        // Since we have a guarantee that 0...n-1 will always be the same insert
        // statement, we do not need to
        // re-generate these statements. Therefore, we will use the same statement for
        // 0...n-1 and a new one for n
        return insertStatementBuilders;
    }

    private Logger getLogger() {
        return logger;
    }

    @Override
    public void write() throws ExecutionException {
        DataStoreInterface dataStore = DataStoreInterface.getDataStoreInterface();
        if (dataStore.size() == 0)
            return;

        long totalRowCount = dataStore.size();
        this.resetErrorRowCount();

        try {
            // Dividing total rows into batches, each batch will be inserted by a separate
            // thread
            List<Batch> batches = Batch.divideIntoBatchesAndChunks(dataStore, CHUNK_SIZE, BATCH_SIZE);
            List<Callable<Object>> tasks = this.getTasks(dataStore,
                    MetadataRepositoryAPI.getPipelineMetadata().getS2TMap(), batches);

            // Execute the tasks through our ExecutorService
            ExecutorService.execute(tasks);

            // Notify observers with only successfully written rows (total - errors)
            long successfulRowCount = totalRowCount - this.getErrorRowCount();
            this.getWriter().notifyDataInsertionObservers(successfulRowCount);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new ExecutionException("Could not write data", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            throw new ExecutionException(e);
        }
    }

    private class InsertStatementBuilder {
        private final StringBuilder builder;
        private final StringBuilder rowTemplate;
        private List<Integer> rows = new LinkedList<>();

        public InsertStatementBuilder(List<Field> columns) {
            this.builder = new StringBuilder("insert into ")
                    .append(InsertStrategy.this.getWriter().getServer().getSafeResourceName())
                    .append(" (");

            boolean firstInserted = false;
            for (String columnName : columns.stream().map(field -> field.getName().trim()).toList()) {
                if (!firstInserted) {
                    firstInserted = true;
                } else {
                    this.builder.append(", ");
                }

                this.builder.append(InsertStrategy.this.getWriter().getServer().escapeIdentifierName(columnName));
            }

            this.builder.append(" ) values ");

            this.rowTemplate = new StringBuilder()
                    .append("( ");

            for (int j = 0; j < columns.size(); j++) {
                if (j != 0)
                    this.rowTemplate.append(", ");

                this.rowTemplate
                        .append(InsertStrategy.this.getWriter().getServer().getDataTypeCast("?",
                                columns.get(j).getDataType()));
            }

            this.rowTemplate.append(" )");
        }

        public InsertStatementBuilder(InsertStatementBuilder insertStatementBuilder) {
            this.builder = new StringBuilder(insertStatementBuilder.builder);
            this.rowTemplate = new StringBuilder(insertStatementBuilder.rowTemplate);
            this.rows = new LinkedList<>();
        }

        public List<Integer> getRows() {
            return rows;
        }

        public String build() {
            if (this.rows.isEmpty())
                return null;

            StringBuilder queryBuilder = new StringBuilder(this.builder);

            for (int i = 0; i < rows.size(); ++i) {
                if (i != 0)
                    queryBuilder.append(", ");

                queryBuilder.append(this.rowTemplate);
            }

            return queryBuilder.toString();
        }
    }
}
