package com.everyarrow.dittoe.io.database.servers.postgresql.writer.strategy;

import com.everyarrow.dittoe.io.data.DataStoreException;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.io.database.servers.jdbc.writer.strategy.JDBCWriteStrategy;
import com.everyarrow.dittoe.pipelines.behaviors.instrumentation.Instrumentation;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.PipelineMetadata;
import org.everyarrow.dittoe.schema.SourceToTargetMapping;
import com.everyarrow.dittoe.utility.Batch;
import com.everyarrow.dittoe.utility.Chunk;
import com.everyarrow.dittoe.utility.Config;
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


public class CopyStrategy extends JDBCWriteStrategy {
    private final Logger logger = LoggerFactory.getLogger(CopyStrategy.class);

    public CopyStrategy() throws ExecutionException {
        super();
    }

    @Override
    public void write() throws ExecutionException {
        DataStoreInterface dataStore = DataStoreInterface.getDataStoreInterface();

        if (dataStore.size() == 0)
            return;

        long totalRowCount = dataStore.size();
        this.resetErrorRowCount();

        try {
            Batch batch = Batch.divideIntoChunks(dataStore, 0, dataStore.size() - 1, this.getChunkSize(dataStore.size()));
            List<Callable<Object>> tasks = this.getTasks(dataStore, batch);

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
     * @param dataStore DataStore object.
     * @param batch     Batch into which total rows are divided.
     * @return List of callables.
     */
    private List<Callable<Object>> getTasks(DataStoreInterface dataStore,
                                            Batch batch) {
        final List<Callable<Object>> tasks = new LinkedList<>();

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

                try (Connection connection = this.getWriter().getServer().getConnection()) {
                    // auto commit set to false for performance gain
                    connection.setAutoCommit(false);
                    CopyStrategy.this.executeCopy(connection, dataStore, MetadataRepositoryAPI.getPipelineMetadata().getS2TMap(), chunk);
                } catch (Exception exception) {
                    CopyStrategy.this.getLogger().error("Error occurred in chunk {}/{}. Error: {}", chunkNumber, chunks.size(), exception.getMessage());
                    CopyStrategy.this.getLogger().error("Retrying chunk {}/{}", chunkNumber, chunks.size());
                    CopyStrategy.this.handleBatchFailure(dataStore, MetadataRepositoryAPI.getPipelineMetadata().getS2TMap(), chunk, chunkNumber, chunks.size());
                }

                return null;
            });
        }

        return tasks;
    }

    private void executeCopy(Connection connection, DataStoreInterface dataStore,
                             List<SourceToTargetMapping> sourceToTargetMappings, Chunk chunk) throws Exception {
        List<String> sourceFields =
                sourceToTargetMappings.stream()
                        .map(sourceToTargetMapping -> sourceToTargetMapping.getSourceField().getName())
                        .toList();

        List<String> targetFields =
                sourceToTargetMappings.stream()
                        .map(sourceToTargetMapping -> sourceToTargetMapping.getTargetField().getName())
                        .toList();

        String query = "COPY " +
                this.getWriter().getServer().getSafeResourceName() +
                "(" +
                String.join(",", targetFields
                        .stream()
                        .map(name -> this.getWriter().getServer().escapeIdentifierName(name))
                        .toList()
                ) +
                ") " +
                "FROM STDIN WITH (FORMAT csv, HEADER false, NULL 'null')";

        StringBuilder data = new StringBuilder();

        for (int index = chunk.startIndex(); index <= chunk.endIndex(); index++) {
            if (dataStore.isValid(index)) {
                Map<String, Object> row = dataStore.get(index);

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

    public void handleBatchFailure(DataStoreInterface dataStore, List<SourceToTargetMapping> sourceToTargetMappings,
                                   Chunk chunk, int batchNumber, int totalBatches) throws DataStoreException, IOException {
        int currentRetrySequenceIndex = 0;
        int retryStartIndex = chunk.startIndex();
        int retryEndIndex = chunk.endIndex();
        int currentRetryStartIndex = retryStartIndex;

        while (true) {
            int currentRetrySequence = CopyStrategy.RETRY_SEQUENCE[currentRetrySequenceIndex];
            int currentRetryEndIndex = Math.min(currentRetryStartIndex + currentRetrySequence - 1, retryEndIndex);
            Chunk currentChunk = new Chunk(currentRetryStartIndex, currentRetryEndIndex);

            try (Connection connection = this.getWriter().getServer().getConnection()) {
                connection.setAutoCommit(false);
                this.executeCopy(connection, dataStore, sourceToTargetMappings, currentChunk);

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

    private int getChunkSize(int dataSize) {
        return Integer.max(1, dataSize / Config.PIPELINE_THREADS);
    }

    private Logger getLogger() {
        return logger;
    }
}