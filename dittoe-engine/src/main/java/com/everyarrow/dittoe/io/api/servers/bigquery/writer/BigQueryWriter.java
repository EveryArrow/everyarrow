package com.everyarrow.dittoe.io.api.servers.bigquery.writer;

import org.everyarrow.dittoe.schema.Field;
import com.everyarrow.utilities.calendar.DateParser;
import com.everyarrow.utilities.calendar.DateTimeParser;
import com.everyarrow.utilities.calendar.TimeParser;
import com.google.cloud.bigquery.*;
import com.everyarrow.dittoe.io.api.APIWriter;
import com.everyarrow.dittoe.io.api.servers.bigquery.BigQueryServer;
import com.everyarrow.dittoe.io.api.servers.bigquery.BigQueryTableMerger;
import com.everyarrow.dittoe.io.api.servers.bigquery.BigQuerySchemaManager;
import com.everyarrow.dittoe.io.api.servers.bigquery.writer.strategy.BigQueryWriteStrategy;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.pipelines.behaviors.instrumentation.Instrumentation;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.BehaviorType;
import org.everyarrow.dittoe.schema.DataType;
import org.everyarrow.dittoe.schema.PipelineMetadata;
import org.everyarrow.dittoe.schema.SourceToTargetMapping;
import com.everyarrow.dittoe.utility.Utils;
import com.everyarrow.dittoe.utility.metadata.MetadataRepositoryAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static com.everyarrow.utilities.property.PropertyManagement.validateProperties;
import static com.google.cloud.bigquery.Field.*;

public class BigQueryWriter extends APIWriter {
    public static final String MERGE = "merge";
    public static final String PROJECT = "project";
    public static final String DATASET = "dataset";
    public static final String PRIMARY_KEY_COLUMNS = "primaryKeyColumns";
    private static final String RECREATE_TABLE_ON_SCHEMA_CHANGE = "recreateTableOnSchemaChange";
    private final Logger logger = LoggerFactory.getLogger(BigQueryWriter.class);
    private String temporaryTableName;
    private BigQueryTableMerger tableMerger;
    private BigQuerySchemaManager schemaManager;


    public BigQueryWriter() throws ExecutionException {
        super();
    }

    public static String convertValue(String value, DataType dataType) {
        if (value == null) {
            return null;
        }

        if (value.isEmpty()) {
            return "";
        }

        try {
            return switch (dataType) {
                case Date -> DateParser.parse(value).toInstant().atZone(ZoneOffset.UTC)
                        .toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
                case Time -> TimeParser.parse(value).toInstant().atZone(ZoneId.systemDefault())
                        .toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                case TimeWithTimeZone -> TimeParser.parse(value).toInstant().atZone(ZoneOffset.UTC)
                        .toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                case DateTime -> DateTimeParser.parse(value).toInstant().atZone(ZoneId.systemDefault())
                        .toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                case DateTimeWithTimeZone -> DateTimeParser.parse(value).toInstant().atZone(ZoneOffset.UTC)
                        .toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                default -> value;
            };
        } catch (Exception ex) {
            throw new IllegalArgumentException("Error converting value: " + value + " to type " + dataType, ex);
        }
    }

    public static String convertValue(Object value, DataType dataType) {
        if (value instanceof byte[] bytes && dataType == DataType.Bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        return convertValue(value == null ? null : value.toString(), dataType);
    }

    @Override
    public void init() throws ExecutionException {
        if (Boolean.parseBoolean(this.getServer().getResource().getResourceProperties().getOrDefault(MERGE, "false"))) {
            validateProperties(this.getServer().getResource().getResourceProperties(), PRIMARY_KEY_COLUMNS);
        }

        // Initialize table replacer, merger, and schema manager
        this.tableMerger = new BigQueryTableMerger(this.getServer());
        this.schemaManager = new BigQuerySchemaManager(this.getServer(), this.tableMerger);

        try {
            this.temporaryTableName = "temp_".concat(this.getServer().getResource().getResourceName());

            if (Boolean.parseBoolean(this.getServer().getResource().getResourceProperties().getOrDefault(RECREATE_TABLE_ON_SCHEMA_CHANGE, "false"))) {
                this.getRetry().execute(() -> {
                    String tableName = this.getServer().getResource().getResourceName();
                    List<Field> expectedFields = this.getServer().getResource().getFields();
                    this.schemaManager.recreateTableOnSchemaChange(tableName, expectedFields);
                    return null;
                });
            }
        } catch (Exception e) {
            throw new ExecutionException(e);
        }

        super.init();
    }

    @Override
    public BigQueryServer getServer() {
        return (BigQueryServer) super.getServer();
    }


    @Override
    public void write() throws ExecutionException {
        try {
            if (!this.dataExists())
                return;

            DataStoreInterface dataStore = DataStoreInterface.getDataStoreInterface();

            TableId tableId = TableId.of(this.getServer().getResource().getServer().getConnectionDetails().get(PROJECT),
                    this.getServer().getResource().getResourceProperties().get(DATASET),
                    this.shouldMerge() ? this.getTemporaryTableName() : this.getServer().getResource().getResourceName());

            JobInfo.WriteDisposition writeDisposition;

            if (this.shouldTruncate()) {
                this.getLogger().info("Target table {} is set to be truncated", tableId.getIAMResourceName());
                writeDisposition = JobInfo.WriteDisposition.WRITE_TRUNCATE;
            } else {
                writeDisposition = JobInfo.WriteDisposition.WRITE_APPEND;
            }

            this.getRetry().execute(() -> {
                // recreate it if exception occurs
                this.getServer().setBigQueryClient(this.getServer().createBigQueryService());
                Table targetTable = this.getServer().getBigQueryClient().getTable(tableId);
                Schema existingSchema = targetTable == null
                        ? null
                        : targetTable.getDefinition().getSchema();
                Schema schema = buildCompatibleLoadSchema(
                        this.getServer().getResource().getFields(),
                        MetadataRepositoryAPI.getPipelineMetadata().getS2TMap(),
                        existingSchema);

                applyCompatibleSchema(targetTable, existingSchema, schema);

                WriteChannelConfiguration.Builder writeChannelConfigurationBuilder =
                        WriteChannelConfiguration.newBuilder(tableId)
                                .setFormatOptions(FormatOptions.json())
                                .setSchema(schema);

                if (writeDisposition == JobInfo.WriteDisposition.WRITE_APPEND) {
                    writeChannelConfigurationBuilder.setSchemaUpdateOptions(List.of(
                            JobInfo.SchemaUpdateOption.ALLOW_FIELD_ADDITION,
                            JobInfo.SchemaUpdateOption.ALLOW_FIELD_RELAXATION));
                }

                this.createLoadJob(writeChannelConfigurationBuilder.setWriteDisposition(writeDisposition).build(),
                        dataStore).call();

                return null;
            });

            if (this.shouldMerge() && Utils.isLastMicroBatch(MetadataRepositoryAPI.getPipelineMetadata())) {
                this.getRetry().execute(() -> {
                    String targetTableName = this.getServer().getResource().getResourceName();
                    String primaryKeyColumns = this.getServer().getResource().getResourceProperties().get(PRIMARY_KEY_COLUMNS);
                    List<Field> fields = this.getServer().getResource().getFields();

                    this.tableMerger.createTargetTableIfNotExists(targetTableName, fields);
                    Table targetTable = this.getServer().getBigQueryClient().getTable(TableId.of(
                            this.getServer().getConnectionDetails().get(PROJECT),
                            this.getServer().getResource().getResourceProperties().get(DATASET),
                            targetTableName));
                    Schema existingSchema = targetTable.getDefinition().getSchema();
                    Schema compatibleSchema = buildCompatibleLoadSchema(
                            fields,
                            MetadataRepositoryAPI.getPipelineMetadata().getS2TMap(),
                            existingSchema);
                    applyCompatibleSchema(targetTable, existingSchema, compatibleSchema);
                    this.tableMerger.mergeTable(targetTableName, this.getTemporaryTableName(), primaryKeyColumns, fields);
                    this.tableMerger.deleteTable(this.getTemporaryTableName());

                    return null;
                });
            }
        } catch (Exception e) {
            throw new ExecutionException(e);
        }
    }

    static Schema buildCompatibleLoadSchema(List<Field> targetFields,
                                            List<SourceToTargetMapping> mappings,
                                            Schema existingSchema) {
        Map<String, Boolean> sourceNullabilityByTarget = mappings.stream()
                .collect(Collectors.toMap(
                        mapping -> mapping.getTargetField().getName().toLowerCase(Locale.ROOT),
                        mapping -> mapping.getSourceField().isNullable(),
                        Boolean::logicalOr,
                        LinkedHashMap::new));

        LinkedHashMap<String, com.google.cloud.bigquery.Field> fieldsByName = new LinkedHashMap<>();
        for (Field targetField : targetFields) {
            String normalizedName = targetField.getName().toLowerCase(Locale.ROOT);
            boolean nullable = targetField.isNullable()
                    || !sourceNullabilityByTarget.containsKey(normalizedName)
                    || sourceNullabilityByTarget.getOrDefault(normalizedName, false);
            fieldsByName.put(normalizedName, newBuilder(
                    targetField.getName(),
                    BigQueryServer.dataTypeToStandardSQLType(targetField.getDataType()))
                    .setMode(nullable ? Mode.NULLABLE : Mode.REQUIRED)
                    .build());
        }

        if (existingSchema != null) {
            for (com.google.cloud.bigquery.Field existingField : existingSchema.getFields()) {
                String normalizedName = existingField.getName().toLowerCase(Locale.ROOT);
                com.google.cloud.bigquery.Field mappedField = fieldsByName.get(normalizedName);
                if (mappedField != null) {
                    boolean nullable = mappedField.getMode() != Mode.REQUIRED
                            || existingField.getMode() != Mode.REQUIRED;
                    fieldsByName.put(normalizedName, mappedField.toBuilder()
                            .setMode(nullable ? Mode.NULLABLE : Mode.REQUIRED)
                            .build());
                } else {
                    // Rows generated from the S2T map omit unmapped destination fields.
                    // Relax them so an existing required/computed column cannot reject
                    // an otherwise valid append.
                    fieldsByName.put(normalizedName, existingField.toBuilder()
                            .setMode(Mode.NULLABLE)
                            .build());
                }
            }
        }

        return Schema.of(new ArrayList<>(fieldsByName.values()));
    }

    private static void applyCompatibleSchema(Table table, Schema existingSchema, Schema compatibleSchema) {
        if (table == null || compatibleSchema.equals(existingSchema)) {
            return;
        }
        table.toBuilder()
                .setDefinition(table.getDefinition().toBuilder().setSchema(compatibleSchema).build())
                .build()
                .update();
    }

    private Callable<Object> createLoadJob(WriteChannelConfiguration writeChannelConfiguration, DataStoreInterface dataStore) {
        DataStoreInterface dataStoreInterface = DataStoreInterface.getDataStoreInterface();
        PipelineMetadata threadLocalPipelineMetadata = MetadataRepositoryAPI.getPipelineMetadata();
        Instrumentation threadLocalInstrumentation = Instrumentation.getInstance();
        String pipelineMetadataRepositoryAPIKey = MetadataRepositoryAPI.getApiKey();
        String instrumentationAPIKey = Instrumentation.getApiKey();

        return () -> {
            DataStoreInterface.setDataStoreInterface(dataStoreInterface);
            MetadataRepositoryAPI.setPipelineMetadata(threadLocalPipelineMetadata);
            MetadataRepositoryAPI.setApiKey(pipelineMetadataRepositoryAPIKey);
            Instrumentation.setInstance(threadLocalInstrumentation);
            Instrumentation.setApiKey(instrumentationAPIKey);

            String jobName = UUID.randomUUID().toString();
            JobId jobId = JobId.newBuilder().setJob(jobName).build();

            try (TableDataWriteChannel tableDataWriteChannel = this.getServer().getBigQueryClient().writer(jobId, writeChannelConfiguration)) {
                this.getWriteStrategy().setTableDataWriteChannel(tableDataWriteChannel);
                this.getWriteStrategy().write();
            }

            try {
                Job job = this.getRetry().execute(() -> {
                            Job loadJob = this.getServer().getBigQueryClient().getJob(jobId);
                            if (loadJob != null)
                                loadJob = loadJob.waitFor();
                            return loadJob;
                        }
                );

                if (job == null) {
                    this.getLogger().error("Job {} not executed.", jobName);
                } else if (job.getStatus().getError() != null) {
                    this.getLogger().error("BigQuery was unable to load job {} to the table due to an error: {}",
                            jobName, job.getStatus().getError());
                } else {
                    // Get output status
                    JobStatistics.LoadStatistics stats = job.getStatistics();

                    this.getLogger().info("Successfully loaded {} rows in job {}", stats.getOutputRows(), jobName);
                    String targetInsertUpdateTaskExecutionId = Instrumentation.getInstance().getRunningTaskExecutionId(
                            MetadataRepositoryAPI
                                    .getPipelineMetadata()
                                    .getBehaviors()
                                    .stream()
                                    .filter(behavior -> behavior.getBehaviorType().equals(BehaviorType.TargetInsertUpdate))
                                    .findFirst()
                                    .orElseThrow(() -> new ExecutionException("TargetInsertUpdate behavior not found"))
                                    .getName()
                    );

                    Instrumentation.getInstance().addMessagesToTask(
                            targetInsertUpdateTaskExecutionId,
                            List.of("BigQuery load job name: " + jobName));

                    Instrumentation.getInstance().addStatisticsToTask(
                            targetInsertUpdateTaskExecutionId,
                            Map.of("Rows processed", stats.getOutputRows()));

                    this.notifyDataInsertionObservers(stats.getOutputRows());
                }
            } catch (BigQueryException e) {
                String detailedError = e.getMessage();

                if (e.getErrors() != null) {
                    detailedError += System.lineSeparator() + e.getErrors().stream().map(BigQueryError::toString).collect(Collectors.joining(","));
                }

                throw new ExecutionException(detailedError, e);
            }

            return null;
        };
    }

    // used to check if data exists in the data store or the json file before creating a bigquery load job for it
    private boolean dataExists() throws ExecutionException {
        boolean dataExists = this.getWriteStrategy().dataExists();
        if (!dataExists) {
            String executionId = Instrumentation.getInstance().getRunningTaskExecutionId(
                    MetadataRepositoryAPI
                            .getPipelineMetadata()
                            .getBehaviors()
                            .stream()
                            .filter(behavior -> behavior.getBehaviorType().equals(BehaviorType.TargetInsertUpdate))
                            .findFirst()
                            .orElseThrow(() -> new ExecutionException("TargetInsertUpdate behavior not found"))
                            .getName()
            );

            Instrumentation.getInstance().addStatisticsToTask(
                    executionId,
                    Map.of("Rows loaded", 0));

            Instrumentation.getInstance().addMessagesToTask(
                    executionId,
                    List.of("No data found to write"));
        }

        return dataExists;
    }




    public Logger getLogger() {
        return logger;
    }


    private boolean shouldMerge() {
          return Boolean.parseBoolean(this.getServer().getResource().getResourceProperties().getOrDefault(MERGE, "false"));
    }

    private boolean shouldTruncate() {
        return Boolean.parseBoolean(this.getServer().getResource().getResourceProperties().getOrDefault("truncate", "false"))
                && Utils.isFirstMicroBatch(MetadataRepositoryAPI.getPipelineMetadata());
    }


    /**
     * Gets the current pipeline ID from the thread-local metadata.
     *
     * @return The current pipeline ID or null if not available
     */
    private String getCurrentPipelineId() {
        try {
            PipelineMetadata pipelineMetadata = MetadataRepositoryAPI.getPipelineMetadata();
            if (pipelineMetadata != null) {
                return pipelineMetadata.getId();
            } else {
                logger.warn("No pipeline metadata found in thread-local storage");
                return null;
            }
        } catch (Exception e) {
            logger.error("Failed to get current pipeline ID from metadata: {}", e.getMessage());
            return null;
        }
    }


    public String getTemporaryTableName() {
        return temporaryTableName;
    }

    @Override
    public BigQueryWriteStrategy getWriteStrategy() {
        return (BigQueryWriteStrategy) super.getWriteStrategy();
    }
}
