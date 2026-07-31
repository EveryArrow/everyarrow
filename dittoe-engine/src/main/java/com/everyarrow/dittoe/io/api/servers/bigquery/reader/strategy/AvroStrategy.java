package com.everyarrow.dittoe.io.api.servers.bigquery.reader.strategy;

import com.everyarrow.utilities.calendar.DateTimeParser;
import com.google.api.client.util.Preconditions;
import com.google.cloud.bigquery.storage.v1.*;
import com.everyarrow.dittoe.io.data.DataStoreException;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.pipelines.behaviors.instrumentation.Instrumentation;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.BehaviorType;
import org.everyarrow.dittoe.schema.DataType;
import com.everyarrow.dittoe.utility.metadata.MetadataRepositoryAPI;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class AvroStrategy extends BigQueryReadStrategy {
    public AvroStrategy() throws ExecutionException {
        super();
    }

    public static BigInteger toBigInteger(ByteBuffer buffer) {
        // Duplicate the buffer so that the original position is not modified.
        ByteBuffer duplicate = buffer.duplicate();
        duplicate.rewind();

        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);

        // Convert to BigInteger. The '1' indicates that the number is positive.
        return new BigInteger(1, bytes);
    }

    @Override
    public void read() throws ExecutionException {
        DataStoreInterface dataStore = DataStoreInterface.getDataStoreInterface();

        try {
            AtomicInteger invalidRowsCount = new AtomicInteger(0);

            AtomicInteger rowIndex = new AtomicInteger(0);

            // Create settings with credentials
            BigQueryReadSettings settings = BigQueryReadSettings.newBuilder()
                    .setCredentialsProvider(() -> this.getReader().getServer().getCredentials())
                    .build();

            try (BigQueryReadClient client = BigQueryReadClient.create(settings)) {

                String parent = String.format("projects/%s", this.getReader().getServer().getResource().getServer().getConnectionDetails().get("project"));
                String tableName = String.format("%s/datasets/%s/tables/%s",
                        parent,
                        this.getReader().getServer().getResource().getResourceProperties().get("dataset"),
                        this.getReader().getServer().getResource().getResourceName());

                ReadSession.TableReadOptions.Builder optionsBuilder = ReadSession.TableReadOptions.newBuilder();

                Optional.ofNullable(this.getConditions())
                        .ifPresent(optionsBuilder::setRowRestriction);

                ReadSession session = client.createReadSession(
                        CreateReadSessionRequest.newBuilder()
                                .setParent(parent)
                                .setReadSession(ReadSession.newBuilder()
                                        .setTable(tableName)
                                        .setDataFormat(DataFormat.AVRO)
                                        .setReadOptions(optionsBuilder.build())
                                        .build())
                                .build());

                List<ReadStream> streams = session.getStreamsList();

                List<Callable<String>> tasks = getCallables(streams, session, client, dataStore, rowIndex, invalidRowsCount);

                try (java.util.concurrent.ExecutorService executorService = Executors.newFixedThreadPool(streams.size())) {
                    // invoking the callables
                    List<Future<String>> results = executorService.invokeAll(tasks);
                    executorService.shutdown();
                    for (Future<String> result1 : results)
                        result1.get();   // only to check if there was any exception during the execution of any callable
                }
            }

            this.getLogger().info("Records copied from the source resource: {}", dataStore.size());

            Instrumentation.getInstance().addStatisticsToTask(
                    Instrumentation.getInstance().getRunningTaskExecutionId(
                            MetadataRepositoryAPI
                                    .getPipelineMetadata()
                                    .getBehaviors()
                                    .stream()
                                    .filter(behavior -> behavior.getBehaviorType().equals(BehaviorType.SourceRetrieval))
                                    .findFirst()
                                    .orElseThrow(() -> new ExecutionException("SourceRetrieval behavior not found"))
                                    .getName()
                    ),
                    Map.of("Rows processed", dataStore.size()));
        } catch (Exception e) {
            throw new ExecutionException(e);
        }
    }

    public void read(int limit) throws ExecutionException {
        DataStoreInterface dataStore = DataStoreInterface.getDataStoreInterface();
        try {
            AtomicInteger invalidRowsCount = new AtomicInteger(0);
            AtomicInteger rowIndex = new AtomicInteger(0);
            BigQueryReadSettings settings = BigQueryReadSettings.newBuilder()
                    .setCredentialsProvider(() -> this.getReader().getServer().getCredentials())
                    .build();
            try (BigQueryReadClient client = BigQueryReadClient.create(settings)) {
                String parent = String.format("projects/%s", this.getReader().getServer().getResource().getServer().getConnectionDetails().get("project"));
                String tableName = String.format("%s/datasets/%s/tables/%s",
                        parent,
                        this.getReader().getServer().getResource().getResourceProperties().get("dataset"),
                        this.getReader().getServer().getResource().getResourceName());
                ReadSession.TableReadOptions.Builder optionsBuilder = ReadSession.TableReadOptions.newBuilder();
                Optional.ofNullable(this.getConditions())
                        .ifPresent(optionsBuilder::setRowRestriction);
                ReadSession session = client.createReadSession(
                        CreateReadSessionRequest.newBuilder()
                                .setParent(parent)
                                .setReadSession(ReadSession.newBuilder()
                                        .setTable(tableName)
                                        .setDataFormat(DataFormat.AVRO)
                                        .setReadOptions(optionsBuilder.build())
                                        .build())
                                .build());
                List<ReadStream> streams = session.getStreamsList();
                List<Callable<String>> tasks = getCallables(streams, session, client, dataStore, rowIndex, invalidRowsCount, limit);
                try (java.util.concurrent.ExecutorService executorService = Executors.newFixedThreadPool(streams.size())) {
                    List<Future<String>> results = executorService.invokeAll(tasks);
                    executorService.shutdown();
                    for (Future<String> result1 : results)
                        result1.get();
                }
            }
            this.getLogger().info("Records copied from the source resource (limited): {}", dataStore.size());
            Instrumentation.getInstance().addStatisticsToTask(
                    Instrumentation.getInstance().getRunningTaskExecutionId(
                        MetadataRepositoryAPI.getPipelineMetadata().getBehaviors().stream()
                                .filter(behavior -> behavior.getBehaviorType().equals(BehaviorType.SourceRetrieval))
                                .findFirst()
                                .orElseThrow(() -> new ExecutionException("SourceRetrieval behavior not found"))
                                .getName()
                    ),
                    Map.of("Rows processed (limited)", dataStore.size()));
        } catch (Exception e) {
            throw new ExecutionException("Error reading from BigQuery Avro (limited)", e);
        }
    }

    private List<Callable<String>> getCallables(List<ReadStream> streams, ReadSession session,
                                                BigQueryReadClient client, DataStoreInterface dataStore,
                                                AtomicInteger rowIndex, AtomicInteger invalidRowsCount) {
        return getCallables(streams, session, client, dataStore, rowIndex, invalidRowsCount, Integer.MAX_VALUE);
    }

    private List<Callable<String>> getCallables(List<ReadStream> streams, ReadSession session,
                                                BigQueryReadClient client, DataStoreInterface dataStore,
                                                AtomicInteger rowIndex, AtomicInteger invalidRowsCount, int limit) {
        List<Callable<String>> tasks = new LinkedList<>();

        for (ReadStream stream : streams) {
            tasks.add(() -> {
                AvroRowsReader reader = new AvroRowsReader(new org.apache.avro.Schema.Parser().parse(session.getAvroSchema().getSchema()));
                ReadRowsRequest readRowsRequest = ReadRowsRequest.newBuilder().setReadStream(stream.getName()).build();

                // Process each block of rows as they arrive and decode using our simple row reader.
                client.readRowsCallable().call(readRowsRequest).forEach(response -> {
                    Preconditions.checkState(response.hasAvroRows());
                    try {
                        reader.processRows(response.getAvroRows(), row -> {
                            try {
                                if(rowIndex.get() >= limit) {
                                    return;
                                }
                                Map<String, Object> rowData = new HashMap<>();
                                this.getReader().getServer().getResource().getFields().forEach(field -> {
                                            Object value = row.get(field.getName());

                                            if (value != null) {
                                                if (field.getDataType().equals(DataType.Date)) {
                                                    value = LocalDate.ofEpochDay(Integer.parseInt(value.toString())).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                                                } else if (field.getDataType().equals(DataType.DateTime) || field.getDataType().equals(DataType.DateTimeWithTimeZone)) {
                                                    try {
                                                        value = DateTimeParser.parse(value.toString()).toInstant().atZone(ZoneOffset.UTC).toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
                                                    } catch (Exception e) {
                                                        value = Instant.ofEpochMilli(Long.parseLong(value.toString()) / 1000).atZone(ZoneOffset.UTC).toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
                                                    }
                                                } else if (field.getDataType().equals(DataType.Time) || field.getDataType().equals(DataType.TimeWithTimeZone)) {
                                                    value = LocalTime.ofNanoOfDay(Long.parseLong(value.toString()) * 1000).format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
                                                } else if (field.getDataType().equals(DataType.Decimal)) {
                                                    value = toBigInteger((ByteBuffer) value);
                                                } else {
                                                    value = value.toString();
                                                }

                                            }

                                            rowData.put(field.getName(), value);
                                        }
                                );

                                dataStore.add(rowIndex.getAndIncrement(), rowData);
                            } catch (DataStoreException e) {
                                throw new RuntimeException(e);
                            }
                        });
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

                return null;
            });
        }
        return tasks;
    }

}
