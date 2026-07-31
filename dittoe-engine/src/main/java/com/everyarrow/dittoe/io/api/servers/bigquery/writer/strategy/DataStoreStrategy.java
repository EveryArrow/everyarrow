package com.everyarrow.dittoe.io.api.servers.bigquery.writer.strategy;

import com.google.gson.JsonObject;
import com.everyarrow.dittoe.io.api.servers.bigquery.writer.BigQueryWriter;
import com.everyarrow.dittoe.io.data.DataStoreException;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import com.everyarrow.dittoe.utility.metadata.MetadataRepositoryAPI;
import org.everyarrow.dittoe.schema.SourceToTargetMapping;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class DataStoreStrategy extends BigQueryWriteStrategy {

    public DataStoreStrategy() throws ExecutionException {
        super();
    }

    @Override
    public boolean dataExists() {
        return DataStoreInterface.getDataStoreInterface().size() != 0;
    }

    @Override
    public void write() throws ExecutionException {
        DataStoreInterface dataStore = DataStoreInterface.getDataStoreInterface();
        List<SourceToTargetMapping> mappings = MetadataRepositoryAPI.getPipelineMetadata().getS2TMap();
        try {
            for (int rowId = 0; rowId < dataStore.size(); rowId++) {
                JsonObject jsonObject = toJsonRow(dataStore.get(rowId), mappings);
                String jsonRow = jsonObject + "\n"; // Adding newline for NDJSON format
                this.getTableDataWriteChannel().write(java.nio.ByteBuffer.wrap(jsonRow.getBytes()));
            }
        } catch (IOException | DataStoreException e) {
            throw new RuntimeException(e);
        }
    }

    static JsonObject toJsonRow(Map<String, Object> row, List<SourceToTargetMapping> mappings) {
        JsonObject jsonObject = new JsonObject();
        for (SourceToTargetMapping mapping : mappings) {
            Object rawValue = row.get(mapping.getSourceField().getName());
            String value = rawValue == null
                    ? null
                    : BigQueryWriter.convertValue(rawValue, mapping.getTargetField().getDataType());
            jsonObject.addProperty(mapping.getTargetField().getName(), value);
        }
        return jsonObject;
    }

}
