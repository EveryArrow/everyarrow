package com.everyarrow.dittoe.io.database.servers.postgresql.writer.strategy;

import com.everyarrow.dittoe.io.data.DataStoreException;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.io.database.DatabaseUtils;
import com.everyarrow.dittoe.io.database.servers.jdbc.writer.strategy.JDBCWriteStrategy;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class InsertDoNothingOnConflictStrategy extends JDBCWriteStrategy {

    public static final String INSERT_CONFLICT_CONSTRAINT = "insertConflictConstraint";
    private final Logger logger = LoggerFactory.getLogger(InsertDoNothingOnConflictStrategy.class);

    public InsertDoNothingOnConflictStrategy() throws ExecutionException {
        super();
    }

    @Override
    public void init() throws ExecutionException {
        this.validateProperties();
        super.init();
    }

    @Override
    public void write() throws ExecutionException {
        DataStoreInterface dataStore = DataStoreInterface.getDataStoreInterface();
        if (dataStore.size() == 0)
            return;

        long totalRowCount = dataStore.size();
        this.resetErrorRowCount();

        try {
            // At max (chunkSize * batchSize) rows will be inserted in a single commit.
            // Dividing total rows into batches, each batch will be inserted by a separate thread
            List<Batch> batches = Batch.divideIntoBatchesAndChunks(dataStore, CHUNK_SIZE, BATCH_SIZE);
            List<Callable<Object>> tasks = this.getTasks(dataStore, MetadataRepositoryAPI.getPipelineMetadata().getS2TMap(), batches);

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
                    InsertDoNothingOnConflictStrategy.this.executeBatchUpsert(connection, dataStore, sourceToTargetMappings, batch, targetFields);
                } catch (Exception exception) {
                    InsertDoNothingOnConflictStrategy.this.getLogger().error("Retrying batch (%d/%d)".formatted(batchNumber, batches.size()));
                    InsertDoNothingOnConflictStrategy.this.handleBatchFailure(dataStore, sourceToTargetMappings, batch, targetFields,
                            batchNumber, batches.size());
                }

                return null;
            });
        }

        return tasks;
    }

    private void executeBatchUpsert(Connection connection, DataStoreInterface dataStore,
                                    List<SourceToTargetMapping> sourceToTargetMappings,
                                    Batch batch, List<Field> targetFields) throws Exception {
        List<InsertStatementBuilder> queries = this.getUpsertQueries(targetFields, batch);

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

    /**
     * Creates insert queries based on input parameters.
     * For the result, this function guarantees size for number of rows in 0...n-1 indices = chunkSize.
     * For the nth index, the size will be number of rows <= chunkSize
     *
     * @param columns Field objects of the table's columns.
     * @param batch   List of batches containing list of chunks indices.
     * @return List of InsertStatementBuilder objects.
     */
    private List<InsertStatementBuilder> getUpsertQueries(List<Field> columns, Batch batch) {
        List<InsertStatementBuilder> upsertStatementBuilders = new LinkedList<>();

        InsertStatementBuilder insertStatementBuilder = new InsertStatementBuilder(columns);
        for (Chunk chunk : batch.getChunks()) {
            for (int i = chunk.startIndex(); i <= chunk.endIndex(); ++i) {
                if (insertStatementBuilder.getRows().size() == CHUNK_SIZE) {
                    upsertStatementBuilders.add(insertStatementBuilder);
                    insertStatementBuilder = new InsertStatementBuilder(insertStatementBuilder);
                    insertStatementBuilder.getRows().clear();
                }

                insertStatementBuilder.getRows().add(i);
            }
        }

        if (!insertStatementBuilder.getRows().isEmpty()) {
            upsertStatementBuilders.add(insertStatementBuilder);
        }

        // Since we have a guarantee that 0...n-1 will always be the same insert statement, we do not need to
        // re-generate these statements. Therefore, we will use the same statement for 0...n-1 and a new one for n
        return upsertStatementBuilders;
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
                this.executeBatchUpsert(connection, dataStore, sourceToTargetMappings, currentBatch, targetFields);

                if (currentRetryStartIndex == retryEndIndex || currentRetryEndIndex == retryEndIndex) {
                    // all batches have been retried and sequence end has been reached
                    break;
                }

                currentRetryStartIndex = currentRetryEndIndex + 1;
                currentRetrySequenceIndex = Math.min(currentRetrySequenceIndex + 1, RETRY_SEQUENCE.length - 1);
            } catch (Exception exception) {

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

    private Logger getLogger() {
        return logger;
    }

    private void validateProperties() throws ExecutionException {
        String[] keys = new String[]{INSERT_CONFLICT_CONSTRAINT};
        Map<String, String> properties = this.getWriter().getServer().getResource().getResourceProperties();

        for (String key : keys) {
            if (!properties.containsKey(key))
                throw new ExecutionException("%s property must be provided".formatted(key));

            if (properties.get(key) == null || properties.get(key).isBlank())
                throw new ExecutionException("%s property must not be blank".formatted(key));
        }
    }

    private class InsertStatementBuilder {
        private final StringBuilder builder;
        private final StringBuilder rowTemplate;
        private List<Integer> rows = new LinkedList<>();

        public InsertStatementBuilder(List<Field> columns) {
            this.builder = new StringBuilder("insert into ")
                    .append(InsertDoNothingOnConflictStrategy.this.getWriter().getServer().getSafeResourceName())
                    .append(" (");

            boolean firstInserted = false;
            for (String columnName : columns.stream().map(field -> field.getName().trim()).toList()) {
                if (!firstInserted) {
                    firstInserted = true;
                } else {
                    this.builder.append(", ");
                }

                this.builder.append(InsertDoNothingOnConflictStrategy.this.getWriter().getServer().escapeIdentifierName(columnName));
            }

            this.builder.append(" ) values ");

            this.rowTemplate = new StringBuilder()
                    .append("( ");

            for (int j = 0; j < columns.size(); j++) {
                if (j != 0)
                    this.rowTemplate.append(", ");

                this.rowTemplate
                        .append(InsertDoNothingOnConflictStrategy.this.getWriter().getServer().getDataTypeCast("?", columns.get(j).getDataType()));
            }

            this.rowTemplate.append(" )");
        }

        public InsertStatementBuilder(InsertStatementBuilder insertStatementBuilder) {
            this.builder = new StringBuilder(insertStatementBuilder.builder);
            this.rowTemplate = new StringBuilder(insertStatementBuilder.rowTemplate);
            this.rows = new LinkedList<>(insertStatementBuilder.rows);
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

            String constraintName = InsertDoNothingOnConflictStrategy.this.getWriter().getServer().getResource().getResourceProperties().get(INSERT_CONFLICT_CONSTRAINT);
            constraintName = InsertDoNothingOnConflictStrategy.this.getWriter().getServer().escapeIdentifierName(constraintName);

            queryBuilder.append(" on conflict on constraint ")
                    .append(constraintName)
                    .append(" do nothing");

            return queryBuilder.toString();
        }
    }
}
