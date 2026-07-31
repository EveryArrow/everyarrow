package com.everyarrow.dittoe.io.file.servers.localfile.reader.strategy;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.everyarrow.dittoe.io.ReadStrategy;
import com.everyarrow.dittoe.io.data.DataStoreException;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.io.data.Row;
import com.everyarrow.dittoe.io.file.Extension;
import com.everyarrow.dittoe.io.file.servers.localfile.reader.LocalFileReader;
import com.everyarrow.dittoe.pipelines.behaviors.instrumentation.Instrumentation;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.DataType;
import org.everyarrow.dittoe.schema.Behavior;
import org.everyarrow.dittoe.schema.BehaviorType;
import org.everyarrow.dittoe.schema.Field;
import org.everyarrow.dittoe.schema.PipelineMetadata;
import com.everyarrow.dittoe.utility.Chunk;
import com.everyarrow.dittoe.utility.CsvRowFilter;
import com.everyarrow.dittoe.utility.ExecutorService;
import com.everyarrow.dittoe.utility.Utils;
import com.everyarrow.dittoe.utility.metadata.MetadataRepositoryAPI;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static com.everyarrow.dittoe.pipelines.behaviors.microbatch.MicroBatch.ROW_NUMBER_COLUMN;

public class JSONReadStrategy extends ReadStrategy {
    private static final long DEFAULT_MAX_JSON_BYTES = 104_857_600L;
    private static final Type ROW_TYPE = new TypeToken<Map<String, Object>>() {
    }.getType();

    public JSONReadStrategy() throws ExecutionException {
        super();
    }

    @Override
    public void read() throws ExecutionException {
        try {
            Gson gson = new Gson();
            JsonArray jsonArray = readJsonArray(gson);
            int chunkSize = 100000;
            DataStoreInterface dataStore = DataStoreInterface.getDataStoreInterface();
            List<Chunk> chunks = divideJsonArrayIntoChunks(jsonArray.size(), chunkSize);
            List<Callable<Object>> tasks = this.getTasks(dataStore, jsonArray, chunks);
            ExecutorService.execute(tasks);
        } catch (IOException | java.util.concurrent.ExecutionException e) {
            throw new ExecutionException("Could not read data", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void read(int limit) throws ExecutionException {
        try {
            Gson gson = new Gson();
            JsonArray jsonArray = readJsonArray(gson);
            int chunkSize = 100000;
            DataStoreInterface dataStore = DataStoreInterface.getDataStoreInterface();
            int count = 0;
            for (int i = 0; i < jsonArray.size() && count < limit; i++) {
                Map<String, Object> rowData = parseRow(gson, jsonArray.get(i));
                dataStore.add(count++, rowData);
            }
        } catch (IOException | DataStoreException e) {
            throw new ExecutionException("Could not read data (limited)", e);
        }
    }

    @Override
    public List<Long> getRange() throws ExecutionException {
        PipelineMetadata pipelineMetadata = MetadataRepositoryAPI.getPipelineMetadata();
        if (pipelineMetadata == null) {
            throw new ExecutionException("Pipeline metadata is required to calculate JSON micro-batch range.");
        }

        List<Behavior> behaviors = pipelineMetadata.getBehaviors() == null
                ? List.of()
                : pipelineMetadata.getBehaviors();
        Behavior microBatch = behaviors
                .stream()
                .filter(behavior -> behavior.getBehaviorType() == BehaviorType.MicroBatch)
                .findFirst()
                .orElseThrow(() -> new ExecutionException("MicroBatch behavior is not set"));

        String rowNumberColumn = microBatch.getProperties().get(ROW_NUMBER_COLUMN);
        if (rowNumberColumn == null || rowNumberColumn.isBlank()) {
            throw new ExecutionException(ROW_NUMBER_COLUMN + " property must be provided");
        }

        try {
            Gson gson = new Gson();
            JsonArray jsonArray = readJsonArray(gson);
            CsvRowFilter filter = new CsvRowFilter(
                    this.getReader().getServer().getResource().getResourceProperties(),
                    getFieldTypes(this.getReader().getServer().getResource().getFields()));

            long minValue = Long.MAX_VALUE;
            long maxValue = Long.MIN_VALUE;
            boolean found = false;

            for (JsonElement element : jsonArray) {
                Map<String, Object> rowData = parseRow(gson, element);
                if (filter.test(rowData)) {
                    Object value = rowData.get(rowNumberColumn);
                    if (value != null) {
                        long rowNumber = parseLongValue(value, rowNumberColumn);
                        minValue = Math.min(minValue, rowNumber);
                        maxValue = Math.max(maxValue, rowNumber);
                        found = true;
                    }
                }
            }

            if (!found) {
                throw new ExecutionException("Could not retrieve the JSON range.");
            }
            return List.of(minValue, maxValue);
        } catch (IOException e) {
            throw new ExecutionException("Could not read JSON for range.", e);
        }
    }

    @Override
    public long getDataCount() throws ExecutionException {
        try {
            Gson gson = new Gson();
            JsonArray jsonArray = readJsonArray(gson);
            return jsonArray.size();
        } catch (IOException e) {
            throw new ExecutionException("Could not get data count.", e);
        }
    }

    @Override
    public int getAverageRowSize() throws ExecutionException {
        try {
            Gson gson = new Gson();
            JsonArray jsonArray = readJsonArray(gson);
            if (jsonArray.isEmpty())
                return 0;

            Map<String, Object> rowData = parseRow(gson, jsonArray.get(0));
            return Utils.getByteSize(new Row(rowData));
        } catch (IOException e) {
            throw new ExecutionException("Could not read data.", e);
        }
    }


    @Override
    public List<Field> getFields() throws ExecutionException {
        try {
            List<Field> fields = new ArrayList<>();
            Gson gson = new Gson();
            JsonArray jsonArray = readJsonArray(gson);

            if (!jsonArray.isEmpty()) {
                Set<String> keySet = parseRow(gson, jsonArray.get(0)).keySet();
                for (String key : keySet)
                    fields.add(new Field(key, DataType.String));
            }

            return fields;
        } catch (IOException e) {
            throw new ExecutionException("Could not read data.", e);
        }
    }

    private List<Callable<Object>> getTasks(DataStoreInterface dataStore, JsonArray jsonArray,
                                            List<Chunk> chunks) {
        List<Callable<Object>> tasks = new LinkedList<>();
        AtomicInteger objectNumber = new AtomicInteger(0);

        DataStoreInterface dataStoreInterface = DataStoreInterface.getDataStoreInterface();
        PipelineMetadata threadLocalPipelineMetadata = MetadataRepositoryAPI.getPipelineMetadata();
        Instrumentation threadLocalInstrumentation = Instrumentation.getInstance();
        String pipelineMetadataRepositoryAPIKey = MetadataRepositoryAPI.getApiKey();
        String instrumentationAPIKey = Instrumentation.getApiKey();

        for (Chunk chunkIndices : chunks) {
            tasks.add(() -> {
                DataStoreInterface.setDataStoreInterface(dataStoreInterface);
                MetadataRepositoryAPI.setPipelineMetadata(threadLocalPipelineMetadata);
                MetadataRepositoryAPI.setApiKey(pipelineMetadataRepositoryAPIKey);
                Instrumentation.setInstance(threadLocalInstrumentation);
                Instrumentation.setApiKey(instrumentationAPIKey);

                Gson gson = new Gson();

                for (int i = chunkIndices.startIndex(); i <= chunkIndices.endIndex(); i++) {
                    Map<String, Object> rowData = parseRow(gson, jsonArray.get(i));
                    dataStore.add(objectNumber.getAndIncrement(), rowData);
                }
                return null;
            });
        }

        return tasks;
    }

    private List<Chunk> divideJsonArrayIntoChunks(int totalRows, int chunkSize) {
        if (chunkSize < 1) {
            throw new IllegalArgumentException(String.format("Invalid chunk size %s", chunkSize));
        }

        List<Chunk> chunks = new ArrayList<>();
        for (int startIndex = 0; startIndex < totalRows; startIndex += chunkSize) {
            int endIndex = Math.min(startIndex + chunkSize - 1, totalRows - 1);
            chunks.add(new Chunk(startIndex, endIndex));
        }
        return chunks;
    }

    private Map<String, Object> parseRow(Gson gson, JsonElement jsonElement) {
        return gson.fromJson(jsonElement, ROW_TYPE);
    }

    private long parseLongValue(Object value, String name) throws ExecutionException {
        if (value instanceof Number number) {
            double doubleValue = number.doubleValue();
            long longValue = number.longValue();
            if (Double.compare(doubleValue, longValue) != 0) {
                throw new ExecutionException("Expected whole-number value for " + name + ".");
            }
            return longValue;
        }

        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new ExecutionException("Invalid row number value: " + value, e);
        }
    }

    private Map<String, DataType> getFieldTypes(List<Field> fields) {
        Map<String, DataType> fieldTypes = new HashMap<>();
        for (Field field : fields) {
            fieldTypes.put(field.getName(), field.getDataType());
        }
        return fieldTypes;
    }

    private Path getJsonFilePath() {
        return this.getReader().getServer().resolveResourcePath(
                this.getReader().getServer().getResource().getResourceName().concat(Extension.JSON));
    }

    private JsonArray readJsonArray(Gson gson) throws IOException, ExecutionException {
        Path jsonFile = getJsonFilePath();
        long maxJsonBytes = getMaxJsonBytes();
        long fileSize = Files.size(jsonFile);
        if (fileSize > maxJsonBytes) {
            throw new ExecutionException("JSON file exceeds maxJsonBytes limit.");
        }

        try (FileReader reader = new FileReader(jsonFile.toFile())) {
            return gson.fromJson(reader, JsonArray.class);
        }
    }

    private long getMaxJsonBytes() throws ExecutionException {
        String configuredValue = this.getReader().getServer().getResource().getResourceProperties()
                .getOrDefault("maxJsonBytes", String.valueOf(DEFAULT_MAX_JSON_BYTES));
        try {
            return Long.parseLong(configuredValue);
        } catch (NumberFormatException e) {
            throw new ExecutionException("maxJsonBytes must be numeric.", e);
        }
    }

    @Override
    public LocalFileReader getReader() {
        return (LocalFileReader) super.getReader();
    }
}
