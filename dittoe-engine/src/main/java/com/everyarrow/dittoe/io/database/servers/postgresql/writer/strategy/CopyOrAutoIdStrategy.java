package com.everyarrow.dittoe.io.database.servers.postgresql.writer.strategy;


import com.everyarrow.dittoe.utility.retry.Config;
import com.everyarrow.dittoe.io.data.DataStoreException;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.io.database.servers.jdbc.writer.strategy.JDBCWriteStrategy;
import com.everyarrow.dittoe.pipelines.behaviors.instrumentation.Instrumentation;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.PipelineMetadata;
import org.everyarrow.dittoe.schema.SourceToTargetMapping;
import com.everyarrow.dittoe.utility.Batch;
import com.everyarrow.dittoe.utility.Chunk;
import com.everyarrow.dittoe.utility.ExecutorService;
import com.everyarrow.dittoe.utility.metadata.MetadataRepositoryAPI;
import org.postgresql.copy.CopyManager;
import org.postgresql.jdbc.PgConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.sql.Connection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;


/**
 * The {@code CopyOrAutoIdStrategy} class implements a strategy for inserting rows into a database
 * with a conditional approach based on the existence of the row in the target table.
 * <p>
 * If the row already exists in the database (determined by a unique identifier or primary key),
 * this strategy will insert the row including the {@code id} column value.
 * <p>
 * However, if the row does not exist in the database, this strategy will insert the row without
 * the {@code id} column, allowing the database to automatically generate the {@code id} value
 * using the default sequence or auto-increment mechanism defined on the table.
 * <p>
 * This strategy is particularly useful in scenarios where you want to maintain existing
 * {@code id} values for rows that are being re-inserted, while still benefiting from automatic
 * ID generation for new rows by the database.
 */

public class CopyOrAutoIdStrategy extends JDBCWriteStrategy {

    protected static final String ID_COLUMN = "idColumn";

    private final Logger logger = LoggerFactory.getLogger(CopyOrAutoIdStrategy.class);

    public CopyOrAutoIdStrategy() throws ExecutionException {
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
        if (dataStore.size() == 0) return;

        long totalRowCount = dataStore.size();
        this.resetErrorRowCount();

        try {
            // At max (chunkSize * batchSize) rows will be inserted in a single commit.
            // Dividing total rows into batches, each batch will be inserted by a separate thread
            Batch batch = Batch.divideIntoChunks(dataStore, 0, dataStore.size() - 1, this.getChunkSize(dataStore.size()));
            List<Callable<Object>> tasks = this.getTasks(dataStore, MetadataRepositoryAPI.getPipelineMetadata().getS2TMap(), batch);

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
     * @param batch                  Batches into which total rows are divided.
     * @return List of callables.
     */
    private List<Callable<Object>> getTasks(DataStoreInterface dataStore,
                                            List<SourceToTargetMapping> sourceToTargetMappings,
                                            Batch batch) {
        final List<Callable<Object>> tasks = new LinkedList<>();
        final List<SourceToTargetMapping> sourceToTargetMappingsWithoutId =
                sourceToTargetMappings.stream().filter(sourceToTargetMapping -> !sourceToTargetMapping.getTargetField().getName().equalsIgnoreCase(this.getIdColumn())).collect(Collectors.toList());

        DataStoreInterface dataStoreInterface = DataStoreInterface.getDataStoreInterface();
        PipelineMetadata threadLocalPipelineMetadata = MetadataRepositoryAPI.getPipelineMetadata();
        Instrumentation threadLocalInstrumentation = Instrumentation.getInstance();
        String pipelineMetadataRepositoryAPIKey = MetadataRepositoryAPI.getApiKey();
        String instrumentationAPIKey = Instrumentation.getApiKey();

        List<Chunk> chunks = batch.getChunks();

        for (int i = 0; i < chunks.size(); ++i) {
            int chunkNumber = i + 1;
            Chunk chunk = chunks.get(i);
            tasks.add(() -> {
                DataStoreInterface.setDataStoreInterface(dataStoreInterface);
                MetadataRepositoryAPI.setPipelineMetadata(threadLocalPipelineMetadata);
                MetadataRepositoryAPI.setApiKey(pipelineMetadataRepositoryAPIKey);
                Instrumentation.setInstance(threadLocalInstrumentation);
                Instrumentation.setApiKey(instrumentationAPIKey);

                try (Connection connection = CopyOrAutoIdStrategy.this.getWriter().getServer().getConnection()) {
                    // auto commit set to false for performance gain
                    connection.setAutoCommit(false);
                    CopyOrAutoIdStrategy.this.executeCopyWithId(connection, dataStore, sourceToTargetMappings, chunk);
                } catch (Exception exception) {
                    CopyOrAutoIdStrategy.this.getLogger().error("Retrying chunk with id strategy (%d/%d)".formatted(chunkNumber, chunks.size()));
                    CopyOrAutoIdStrategy.this.handleBatchFailureWithId(dataStore, sourceToTargetMappings, chunk);
                }

                try (Connection connection = CopyOrAutoIdStrategy.this.getWriter().getServer().getConnection()) {
                    // auto commit set to false for performance gain
                    connection.setAutoCommit(false);
                    CopyOrAutoIdStrategy.this.executeCopyWithoutId(connection, dataStore, sourceToTargetMappingsWithoutId, chunk);
                } catch (Exception exception) {
                    CopyOrAutoIdStrategy.this.getLogger().error("Retrying chunk without id strategy (%d/%d)".formatted(chunkNumber,
                            chunks.size()));
                    CopyOrAutoIdStrategy.this.handleBatchFailureWithoutId(dataStore, sourceToTargetMappingsWithoutId, chunk);
                }

                return null;
            });
        }

        return tasks;
    }


    private void executeCopyWithId(Connection connection, DataStoreInterface dataStore,
                                   List<SourceToTargetMapping> sourceToTargetMappings, Chunk chunk) throws Exception {
        List<String> sourceFields =
                sourceToTargetMappings.stream().map(sourceToTargetMapping -> sourceToTargetMapping.getSourceField().getName()).toList();
        List<String> targetFields =
                sourceToTargetMappings.stream().map(sourceToTargetMapping -> sourceToTargetMapping.getTargetField().getName()).toList();

        String query = "COPY " +
                this.getWriter().getServer().getSafeResourceName() +
                "(" +
                String.join(",", targetFields) +
                ") " +
                "FROM STDIN WITH (FORMAT csv, HEADER false, NULL 'null')";

        StringBuilder data = new StringBuilder();

        for (int index = chunk.startIndex(); index <= chunk.endIndex(); index++) {
            if (dataStore.isValid(index)) {
                Map<String, Object> row = dataStore.get(index);

                if (!row.containsKey(this.getIdColumn())) continue;

                data.append(sourceFields.stream().map(sourceField -> {
                            Object value = row.get(sourceField);

                            if (value == null)
                                return null;

                            String stringValue = value.toString();
                            // Escape double quotes by doubling them
                            stringValue = stringValue.replace("\"", "\"\"");
                            // Enclose in double quotes if it contains commas, quotes, or line breaks
                            if (stringValue.contains(",") || stringValue.contains("\"") || stringValue.contains("\n")) {
                                stringValue = "\"" + stringValue + "\"";
                            }

                            return stringValue;
                        }).collect(Collectors.joining(",")))
                        .append("\n");
            }
        }

        if (!data.isEmpty()) {
            CopyManager copyManager = new CopyManager(connection.unwrap(PgConnection.class));
            copyManager.copyIn(query, new StringReader(data.toString()));
        }

        connection.commit();
    }

    private void executeCopyWithoutId(Connection connection, DataStoreInterface dataStore,
                                      List<SourceToTargetMapping> sourceToTargetMappings, Chunk chunk) throws Exception {
        List<String> sourceFields =
                sourceToTargetMappings.stream().map(sourceToTargetMapping -> sourceToTargetMapping.getSourceField().getName()).toList();
        List<String> targetFields =
                sourceToTargetMappings.stream().map(sourceToTargetMapping -> sourceToTargetMapping.getTargetField().getName()).toList();

        String query = "COPY " +
                this.getWriter().getServer().getSafeResourceName() +
                "(" +
                String.join(",", targetFields) +
                ") " +
                "FROM STDIN WITH (FORMAT csv, HEADER false, NULL 'null')";

        StringBuilder data = new StringBuilder();

        for (int index = chunk.startIndex(); index <= chunk.endIndex(); index++) {
            if (dataStore.isValid(index)) {
                Map<String, Object> row = dataStore.get(index);

                if (row.containsKey(this.getIdColumn())) continue;

                data.append(sourceFields.stream().map(sourceField -> {
                            Object value = row.get(sourceField);

                            if (value == null)
                                return null;

                            String stringValue = value.toString();
                            // Escape double quotes by doubling them
                            stringValue = stringValue.replace("\"", "\"\"");
                            // Enclose in double quotes if it contains commas, quotes, or line breaks
                            if (stringValue.contains(",") || stringValue.contains("\"") || stringValue.contains("\n")) {
                                stringValue = "\"" + stringValue + "\"";
                            }

                            return stringValue;
                        }).collect(Collectors.joining(",")))
                        .append("\n");
            }
        }

        if (!data.isEmpty()) {
            CopyManager copyManager = new CopyManager(connection.unwrap(PgConnection.class));
            copyManager.copyIn(query, new StringReader(data.toString()));
        }

        connection.commit();
    }

    public void handleBatchFailureWithId(DataStoreInterface dataStore, List<SourceToTargetMapping> sourceToTargetMappings,
                                         Chunk chunk) throws DataStoreException, IOException {
        int currentRetrySequenceIndex = 0;
        int retryStartIndex = chunk.startIndex();
        int retryEndIndex = chunk.endIndex();
        int currentRetryStartIndex = retryStartIndex;

        while (true) {
            int currentRetrySequence = Config.RETRY_SEQUENCE[currentRetrySequenceIndex];
            int currentRetryEndIndex = Math.min(currentRetryStartIndex + currentRetrySequence - 1, retryEndIndex);
            Chunk currentChunk = new Chunk(currentRetryStartIndex, currentRetryEndIndex);

            try (Connection connection = this.getWriter().getServer().getConnection()) {
                connection.setAutoCommit(false);
                this.executeCopyWithId(connection, dataStore, sourceToTargetMappings, currentChunk);

                if (currentRetryStartIndex == retryEndIndex || currentRetryEndIndex == retryEndIndex) {
                    // all batches have been retried and sequence end has been reached
                    break;
                }

                currentRetryStartIndex = currentRetryEndIndex + 1;
                currentRetrySequenceIndex = Math.min(currentRetrySequenceIndex + 1, Config.RETRY_SEQUENCE.length - 1);
            } catch (Exception exception) {

                if (currentRetrySequenceIndex == 0) {
                    // found the erroneous row
                    this.writeErrorRow(sourceToTargetMappings, dataStore.get(currentRetryStartIndex),
                            "handleBatchFailureWithId: " + exception.getMessage());
                    currentRetryStartIndex += 1;
                } else {
                    currentRetrySequenceIndex = Math.max(currentRetrySequenceIndex - 1, 0);
                }
            }
        }
    }

    public void handleBatchFailureWithoutId(DataStoreInterface dataStore, List<SourceToTargetMapping> sourceToTargetMappings,
                                            Chunk chunk) throws DataStoreException, IOException {
        int currentRetrySequenceIndex = 0;
        int retryStartIndex = chunk.startIndex();
        int retryEndIndex = chunk.endIndex();
        int currentRetryStartIndex = retryStartIndex;

        while (true) {
            int currentRetrySequence = Config.RETRY_SEQUENCE[currentRetrySequenceIndex];
            int currentRetryEndIndex = Math.min(currentRetryStartIndex + currentRetrySequence - 1, retryEndIndex);
            Chunk currentChunk = new Chunk(currentRetryStartIndex, currentRetryEndIndex);

            try (Connection connection = this.getWriter().getServer().getConnection()) {
                connection.setAutoCommit(false);
                this.executeCopyWithoutId(connection, dataStore, sourceToTargetMappings, currentChunk);

                if (currentRetryStartIndex == retryEndIndex || currentRetryEndIndex == retryEndIndex) {
                    // all batches have been retried and sequence end has been reached
                    break;
                }

                currentRetryStartIndex = currentRetryEndIndex + 1;
                currentRetrySequenceIndex = Math.min(currentRetrySequenceIndex + 1, Config.RETRY_SEQUENCE.length - 1);
            } catch (Exception exception) {

                if (currentRetrySequenceIndex == 0) {
                    // found the erroneous row
                    this.writeErrorRow(sourceToTargetMappings, dataStore.get(currentRetryStartIndex),
                            "handleBatchFailureWithoutId: " + exception.getMessage());
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
        String[] keys = new String[]{ID_COLUMN};
        Map<String, String> properties = this.getWriter().getServer().getResource().getResourceProperties();

        for (String key : keys) {
            if (!properties.containsKey(key))
                throw new ExecutionException("%s property must be provided".formatted(key));
        }
    }

    private String getIdColumn() {
        return this.getWriter().getServer().getResource().getResourceProperties().get(ID_COLUMN);
    }

    private int getChunkSize(int dataSize) {
        return Integer.max(1, dataSize / com.everyarrow.dittoe.utility.Config.PIPELINE_THREADS);
    }

}