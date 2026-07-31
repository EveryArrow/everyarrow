package com.everyarrow.dittoe.io.api.servers.bigquery.reader.strategy;

import com.google.cloud.bigquery.*;
import com.everyarrow.dittoe.io.ReadStrategy;
import com.everyarrow.dittoe.io.api.servers.bigquery.BigQueryServer;
import com.everyarrow.dittoe.io.api.servers.bigquery.reader.BigQueryReader;
import com.everyarrow.dittoe.io.data.Row;
import com.everyarrow.dittoe.pipelines.behaviors.microbatch.MicroBatchManager;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.Behavior;
import org.everyarrow.dittoe.schema.BehaviorType;
import org.everyarrow.dittoe.schema.DataType;
import org.everyarrow.dittoe.schema.Field;
import com.everyarrow.dittoe.utility.Utils;
import com.everyarrow.dittoe.utility.metadata.MetadataRepositoryAPI;
import com.everyarrow.dittoe.utility.retry.Retry;
import com.everyarrow.dittoe.utility.retry.RetryType;
import com.everyarrow.dittoe.utility.security.SqlSafety;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.everyarrow.utilities.property.PropertyManagement.validateProperties;

/**
 * Implementation of the Select strategy for JDBC.
 */

public abstract class BigQueryReadStrategy extends ReadStrategy {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private Retry retry;

    public BigQueryReadStrategy() throws ExecutionException {
        super();
    }

    @Override
    public void init() throws ExecutionException {
        try {
            this.retry = RetryType.getDefault().classReference().getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new ExecutionException(e);
        }

        super.init();
    }

    @Override
    public BigQueryReader getReader() {
        return (BigQueryReader) super.getReader();
    }


    public String getConditions() throws ExecutionException {
        String conditions = Stream.of(this.getMicroBatchCondition(), this.getNewRecordsCondition(), this.getFilterExpression())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" and "));

        return conditions.isEmpty() ? null : conditions;
    }

    private String getMicroBatchCondition() throws ExecutionException {
        MicroBatchManager.Batch batch = MicroBatchManager.getBatch();

        if (batch == null)
            return null;

        String fieldName = SqlSafety.requireKnownField(batch.fieldName(), this.getReader().getServer().getResource().getFields());
        long start = SqlSafety.requireLongLiteral(batch.valueStart(), "micro batch start");
        long end = SqlSafety.requireLongLiteral(batch.valueEnd(), "micro batch end");
        StringBuilder condition = new StringBuilder(this.getReader().getServer().escapeIdentifierName(fieldName))
                .append(" between ")
                .append(start)
                .append(" and ")
                .append(end);

        this.getLogger().info("Micro Batch condition: {}", condition);

        return Utils.addParentheses(condition.toString());
    }

    private String getNewRecordsCondition() throws ExecutionException {
        Map<String, String> properties = this.getReader().getServer().getResource().getResourceProperties();

        if (!Utils.shouldGetNewRecords(properties))
            return null;

        // if new records field value is not specified initially it means that it should be set at the end of pipeline
        if (!properties.containsKey("newRecordsFieldValue"))
            return null;

        String fieldName = SqlSafety.requireKnownField(properties.get("newRecordsField"),
                this.getReader().getServer().getResource().getFields());
        String condition = this.getReader().getServer().escapeIdentifierName(fieldName) +
                " > " +
                SqlSafety.quoteSqlLiteral(properties.get("newRecordsFieldValue")) +
                this.getReader().getServer().getDataTypeCast(DataType.valueOf(properties.get("newRecordsFieldType")));

        this.getLogger().info("Getting new records where {}", condition);

        return Utils.addParentheses(condition);
    }


    public Logger getLogger() {
        return logger;
    }

    /**
     * Gets the average size in bytes of a row.
     *
     * @return Number of bytes.
     * @throws ExecutionException Thrown in case any error occurred while computing the size.
     */
    @Override
    public int getAverageRowSize() throws ExecutionException {
        try {
            String query = String.format("SELECT * FROM %s LIMIT 1", this.getReader().getServer().getSafeResourceName());

            QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query).build();
            TableResult result = this.getReader().getServer().getBigQueryClient().query(queryConfig);

            for (FieldValueList row : result.iterateAll()) {
                Map<String, Object> rowData = new HashMap<>();

                this.getReader().getServer().getResource().getFields().forEach(field ->
                        rowData.put(field.getName(),
                                Optional.ofNullable(row.get(field.getName())).map(FieldValue::getValue).orElse(null)));

                return Utils.getByteSize(new Row(rowData));
            }

            return 0;
        } catch (Exception e) {
            throw new ExecutionException("Could not get average row size", e);
        }
    }

    /**
     * Retrieves the fields (columns) of the resource.
     *
     * @return A list of Field objects representing the resource's fields.
     * @throws ExecutionException Thrown if retrieving the fields fails.
     */
    @Override
    public List<Field> getFields() throws ExecutionException {
        try {
            TableId tableId = TableId.of(
                    this.getReader().getServer().getResource().getServer().getConnectionDetails().get("project"),
                    this.getReader().getServer().getResource().getResourceProperties().get("dataset"),
                    this.getReader().getServer().getResource().getResourceName()
            );

            Table table = this.getReader().getServer().getBigQueryClient().getTable(tableId);

            if (table == null) {
                throw new ExecutionException("Table not found: " + tableId.getTable());
            }

            Schema schema = table.getDefinition().getSchema();

            if (schema == null) {
                throw new ExecutionException("Schema not found for table: " + tableId.getTable());
            }

            return schema.getFields().stream()
                    .map(field -> new Field(field.getName(), BigQueryServer.standardSQLTypeToDataType(field.getType().getStandardType())))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new ExecutionException(e);
        }
    }

    @Override
    public List<Long> getRange() throws ExecutionException {
        Optional<Behavior> microBatch = MetadataRepositoryAPI.getPipelineMetadata().getBehaviors().stream().filter(
                behavior -> behavior.getBehaviorType() == BehaviorType.MicroBatch
        ).findFirst();

        if (microBatch.isEmpty()) {
            throw new ExecutionException("MicroBatch behavior is not set");
        }

        Map<String, String> microBatchProperties = microBatch.get().getProperties();
        validateProperties(microBatchProperties, "rowNumberColumn");

        String rowNumberColumn = SqlSafety.requireKnownField(microBatchProperties.get("rowNumberColumn"),
                this.getReader().getServer().getResource().getFields());
        String escapedRowNumberColumn = this.getReader().getServer().escapeIdentifierName(rowNumberColumn);

        try {
            StringBuilder query = new StringBuilder("select min(")
                    .append(escapedRowNumberColumn)
                    .append(") as minValue, max(")
                    .append(escapedRowNumberColumn)
                    .append(") as maxValue from ")
                    .append(this.getReader().getServer().getSafeResourceName());

            String conditions = Stream.of(this.getNewRecordsCondition(),
                            this.getFilterExpression())
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(" and "));

            if (!conditions.isEmpty())
                query.append(" where ").append(conditions);

            QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query.toString()).build();

            TableResult result = this.getReader().getServer().getBigQueryClient().query(queryConfig);

            FieldValueList row = result.iterateAll().iterator().next();

            return List.of(row.get("minValue").getLongValue(), row.get("maxValue").getLongValue());
        } catch (Exception e) {
            throw new ExecutionException(e);
        }
    }

    /**
     * Gets the data count from the resource.
     *
     * @return Total data count.
     * @throws ExecutionException Thrown in case any error occurred while retrieving the count.
     */
    @Override
    public long getDataCount() throws ExecutionException {
        try {
            StringBuilder query = new StringBuilder("SELECT COUNT(*) as count FROM ")
                    .append(this.getReader().getServer().getSafeResourceName());

            Optional.ofNullable(this.getConditions())
                    .ifPresent(conditions -> query.append(" WHERE ").append(conditions));

            QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query.toString()).build();
            TableResult result = this.getReader().getServer().getBigQueryClient().query(queryConfig);

            return result.iterateAll().iterator().next().get("count").getLongValue();
        } catch (Exception e) {
            throw new ExecutionException(e);
        }
    }


    public Retry getRetry() {
        return retry;
    }

    public String getFilterExpression() throws ExecutionException {
        String filterExpression = SqlSafety.getUnsafeFilterExpression(this.getReader().getServer().getResource().getResourceProperties());

        if (filterExpression == null)
            return null;

        this.getLogger().warn("Using trusted legacy raw BigQuery filterExpression. This is disabled by default for public-safe PMDs.");

        return Utils.addParentheses(filterExpression);
    }
}
