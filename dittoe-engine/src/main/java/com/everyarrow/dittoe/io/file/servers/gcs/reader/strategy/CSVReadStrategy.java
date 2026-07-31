package com.everyarrow.dittoe.io.file.servers.gcs.reader.strategy;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.everyarrow.dittoe.io.ReadStrategy;
import com.everyarrow.dittoe.io.file.servers.gcs.GCSServer;
import com.everyarrow.dittoe.io.file.servers.gcs.reader.GCSReader;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.DataType;
import org.everyarrow.dittoe.schema.Field;
import com.everyarrow.dittoe.utility.CsvUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Concrete CSV read strategy for Google Cloud Storage.
 * <p>
 * Reads a CSV file from GCS and populates the DataStore.
 */
public class CSVReadStrategy extends ReadStrategy {
    private static final String BUCKET_PROPERTY = "gcsBucket";
    private static final String OBJECT_PROPERTY = "gcsObject";
    private static final String DELIMITER_OPTION = "csvDelimiter";
    private String delimiter = ","; // default delimiter

    public CSVReadStrategy() throws ExecutionException {
        super();
    }

    @Override
    public void init() throws ExecutionException {
        // Initialize delimiter from resource properties.
        Map<String, String> props = this.getReader().getServer().getResource().getResourceProperties();
        this.delimiter = props.getOrDefault(DELIMITER_OPTION, ",");
        super.init();
    }

    private Reader getFileReader() throws ExecutionException {
        Map<String, String> props = this.getReader().getServer().getResource().getResourceProperties();
        String bucket = props.get(BUCKET_PROPERTY);
        String objectName = props.get(OBJECT_PROPERTY);
        if (bucket == null || objectName == null) {
            throw new ExecutionException("GCS bucket or object name not provided in resource properties.");
        }
        Storage storage = ((GCSServer) this.getReader().getServer()).getStorage();
        Blob blob = storage.get(BlobId.of(bucket, objectName));
        if (blob == null) {
            throw new ExecutionException("No blob found for bucket " + bucket + " and object " + objectName);
        }
        try {
            return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(blob.getContent()), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new ExecutionException("Failed to get file reader from GCS", e);
        }
    }

    @Override
    public void read() throws ExecutionException {
        CsvUtils.read(getFileReader(), this.getReader().getServer().getResource().getFields(), this.getReader().getServer().getResource().getResourceProperties());
    }

    public void read(int limit) throws ExecutionException {
        CsvUtils.read(getFileReader(), this.getReader().getServer().getResource().getFields(), this.getReader().getServer().getResource().getResourceProperties(), limit);
    }

    @Override
    public List<Long> getRange() throws ExecutionException {
        try {
            long count = getDataCount();
            return Arrays.asList(0L, Math.max(count - 1, 0L));
        } catch (Exception e) {
            throw new ExecutionException("Failed to get range", e);
        }
    }

    @Override
    public long getDataCount() throws ExecutionException {
        try {
            Map<String, String> props = this.getReader().getServer().getResource().getResourceProperties();
            String bucket = props.get(BUCKET_PROPERTY);
            String objectName = props.get(OBJECT_PROPERTY);
            if (bucket == null || objectName == null) {
                throw new ExecutionException("GCS bucket or object name not provided in resource properties.");
            }
            Storage storage = ((GCSServer) this.getReader().getServer()).getStorage();
            Blob blob = storage.get(BlobId.of(bucket, objectName));
            if (blob == null) {
                throw new ExecutionException("No blob found for bucket " + bucket + " and object " + objectName);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(blob.getContent()), StandardCharsets.UTF_8));
            long count = 0;
            boolean headerSkipped = false;
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty()) {
                    if (!headerSkipped) {
                        headerSkipped = true;
                        continue;
                    }
                    count++;
                }
            }
            reader.close();
            return count;
        } catch (Exception e) {
            throw new ExecutionException("Failed to count data in GCS", e);
        }
    }

    @Override
    public int getAverageRowSize() throws ExecutionException {
        try {
            Map<String, String> props = this.getReader().getServer().getResource().getResourceProperties();
            String bucket = props.get(BUCKET_PROPERTY);
            String objectName = props.get(OBJECT_PROPERTY);
            if (bucket == null || objectName == null) {
                throw new ExecutionException("GCS bucket or object name not provided in resource properties.");
            }
            Storage storage = ((GCSServer) this.getReader().getServer()).getStorage();
            Blob blob = storage.get(BlobId.of(bucket, objectName));
            if (blob == null) {
                throw new ExecutionException("No blob found for bucket " + bucket + " and object " + objectName);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(blob.getContent()), StandardCharsets.UTF_8));
            boolean headerSkipped = false;
            int averageSize = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty()) {
                    if (!headerSkipped) {
                        headerSkipped = true;
                        continue;
                    }
                    averageSize = line.getBytes(StandardCharsets.UTF_8).length;
                    break;
                }
            }
            reader.close();
            return averageSize;
        } catch (Exception e) {
            throw new ExecutionException("Failed to get average row size from GCS", e);
        }
    }

    @Override
    public List<Field> getFields() throws ExecutionException {
        try {
            Map<String, String> props = this.getReader().getServer().getResource().getResourceProperties();
            String bucket = props.get(BUCKET_PROPERTY);
            String objectName = props.get(OBJECT_PROPERTY);
            if (bucket == null || objectName == null) {
                throw new ExecutionException("GCS bucket or object name not provided in resource properties.");
            }
            Storage storage = ((GCSServer) this.getReader().getServer()).getStorage();
            Blob blob = storage.get(BlobId.of(bucket, objectName));
            if (blob == null) {
                throw new ExecutionException("No blob found for bucket " + bucket + " and object " + objectName);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(blob.getContent()), StandardCharsets.UTF_8));
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isEmpty()) {
                throw new ExecutionException("GCS object has no header line.");
            }
            List<Field> fields = parseHeader(headerLine);
            reader.close();
            return fields;
        } catch (Exception e) {
            throw new ExecutionException("Failed to get fields from GCS", e);
        }
    }

    // --- Helper Methods ---
    private List<Field> parseHeader(String headerLine) {
        List<Field> fields = new ArrayList<>();
        String[] headers = parseAndTrim(headerLine, delimiter);
        for (String header : headers) {
            fields.add(new Field(header, DataType.String));
        }
        return fields;
    }

    public String[] parseAndTrim(String line, String delimiter) {
        String[] parts = line.split(delimiter);
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }
        return parts;
    }

    private Map<String, Object> createDataMap(String[] parsedLine, Map<String, Integer> headerIndices) {
        Map<String, Object> dataMap = new HashMap<>();
        for (Map.Entry<String, Integer> entry : headerIndices.entrySet()) {
            int idx = entry.getValue();
            String value = idx < parsedLine.length ? parsedLine[idx] : "";
            dataMap.put(entry.getKey(), value);
        }
        return dataMap;
    }

    @Override
    public GCSReader getReader() {
        return (GCSReader) super.getReader();
    }
}
