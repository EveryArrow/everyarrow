package com.everyarrow.dittoe.io.api.servers.bigquery.reader.strategy;

import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.TableResult;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.DataType;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class QueryStrategy extends BigQueryReadStrategy {
    public QueryStrategy() throws ExecutionException {
        super();
    }

    @Override
    public void read() throws ExecutionException {
        DataStoreInterface dataStore = DataStoreInterface.getDataStoreInterface();

        String fields = this.getReader().getServer().getResource().getFields().stream().map(field -> {
            if (field.getDataType().equals(DataType.DateTimeWithTimeZone))
                return "FORMAT_TIMESTAMP('%%Y-%%m-%%d %%H:%%M:%%S', %s) AS %s".formatted(
                        this.getReader().getServer().escapeIdentifierName(field.getName()),
                        this.getReader().getServer().escapeIdentifierName(field.getName()));
            return this.getReader().getServer().escapeIdentifierName(field.getName());
        }).collect(Collectors.joining(", "));

        String query = String.format("SELECT %s FROM %s",
                fields,
                this.getReader().getServer().getSafeResourceName());

        String conditions = this.getConditions();
        if (conditions != null)
            query += " where " + conditions;

        QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query).build();

        try {
            TableResult result = this.getReader().getServer().getBigQueryClient().query(queryConfig);
            AtomicInteger rowIndex = new AtomicInteger(0);

            for (FieldValueList row : result.iterateAll()) {
                Map<String, Object> rowData = new HashMap<>();

                for (Field field : result.getSchema().getFields()) {
                    rowData.put(field.getName(), row.get(field.getName()).getValue());
                }

                dataStore.add(rowIndex.getAndIncrement(), rowData);
            }

            this.getLogger().info("Records copied from BigQuery: {}", dataStore.size());
        } catch (Exception e) {
            throw new ExecutionException("Error reading from BigQuery", e);
        }
    }

    public void read(int limit) throws ExecutionException {
        DataStoreInterface dataStore = DataStoreInterface.getDataStoreInterface();

        String fields = this.getReader().getServer().getResource().getFields().stream().map(field -> {
            if (field.getDataType().equals(DataType.DateTimeWithTimeZone))
                return "FORMAT_TIMESTAMP('%%Y-%%m-%%d %%H:%%M:%%S', %s) AS %s".formatted(
                        this.getReader().getServer().escapeIdentifierName(field.getName()),
                        this.getReader().getServer().escapeIdentifierName(field.getName()));
            return this.getReader().getServer().escapeIdentifierName(field.getName());
        }).collect(Collectors.joining(", "));

        String query = String.format("SELECT %s FROM %s",
                fields,
                this.getReader().getServer().getSafeResourceName());

        String conditions = this.getConditions();
        if (conditions != null)
            query += " where " + conditions;
        if (limit > 0) {
            query += " limit " + limit;
        }

        QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query).build();

        try {
            TableResult result = this.getReader().getServer().getBigQueryClient().query(queryConfig);
            AtomicInteger rowIndex = new AtomicInteger(0);

            for (FieldValueList row : result.iterateAll()) {
                if (rowIndex.get() >= limit) break;
                Map<String, Object> rowData = new HashMap<>();

                for (Field field : result.getSchema().getFields()) {
                    rowData.put(field.getName(), row.get(field.getName()).getValue());
                }

                dataStore.add(rowIndex.getAndIncrement(), rowData);
            }

            this.getLogger().info("Records copied from BigQuery (limited): {}", dataStore.size());
        } catch (Exception e) {
            throw new ExecutionException("Error reading from BigQuery (limited)", e);
        }
    }
}
