package com.everyarrow.dittoe.io.file.servers.digitalocean.reader.strategy;

import com.everyarrow.dittoe.io.ReadStrategy;
import com.everyarrow.dittoe.io.file.servers.digitalocean.reader.DigitalOceanReader;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.DataType;
import org.everyarrow.dittoe.schema.Field;
import com.everyarrow.dittoe.utility.CsvUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Concrete CSV read strategy for DigitalOcean Spaces.
 * <p>
 * Reads a CSV file from DigitalOcean Spaces using the S3 client and populates the DataStore.
 * </p>
 */
public class CSVReadStrategy extends ReadStrategy {
    private String delimiter = ","; // default delimiter

    public CSVReadStrategy() throws ExecutionException {
        super();
    }

    @Override
    public void init() throws ExecutionException {
        Map<String, String> props = this.getReader().getServer().getResource().getResourceProperties();
        // Optional: use a csvDelimiter property if provided.
        delimiter = props.getOrDefault("csvDelimiter", ",");
        super.init();
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
            DigitalOceanReader reader = (DigitalOceanReader) this.getReader();
            var server = reader.getServer();
            String bucket = server.getBucket();
            String objectName = server.getObjectName();
            if (bucket == null || objectName == null) {
                throw new ExecutionException("Bucket or object name not provided in resource properties.");
            }
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectName)
                    .build();
            ResponseInputStream<GetObjectResponse> s3Object = server.getS3Client().getObject(request);
            BufferedReader br = new BufferedReader(new InputStreamReader(s3Object, StandardCharsets.UTF_8));
            long count = 0;
            boolean headerSkipped = false;
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.isEmpty()) {
                    if (!headerSkipped) {
                        headerSkipped = true;
                        continue;
                    }
                    count++;
                }
            }
            br.close();
            return count;
        } catch (Exception e) {
            throw new ExecutionException("Failed to count rows in DigitalOcean Spaces CSV", e);
        }
    }

    @Override
    public int getAverageRowSize() throws ExecutionException {
        try {
            DigitalOceanReader reader = (DigitalOceanReader) this.getReader();
            var server = reader.getServer();
            String bucket = server.getBucket();
            String objectName = server.getObjectName();
            if (bucket == null || objectName == null) {
                throw new ExecutionException("Bucket or object name not provided in resource properties.");
            }
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectName)
                    .build();
            ResponseInputStream<GetObjectResponse> s3Object = server.getS3Client().getObject(request);
            BufferedReader br = new BufferedReader(new InputStreamReader(s3Object, StandardCharsets.UTF_8));
            boolean headerSkipped = false;
            int averageSize = 0;
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.isEmpty()) {
                    if (!headerSkipped) {
                        headerSkipped = true;
                        continue;
                    }
                    averageSize = line.getBytes(StandardCharsets.UTF_8).length;
                    break;
                }
            }
            br.close();
            return averageSize;
        } catch (Exception e) {
            throw new ExecutionException("Failed to get average row size from DigitalOcean Spaces CSV", e);
        }
    }

    @Override
    public List<Field> getFields() throws ExecutionException {
        try {
            DigitalOceanReader reader = (DigitalOceanReader) this.getReader();
            var server = reader.getServer();
            String bucket = server.getBucket();
            String objectName = server.getObjectName();
            if (bucket == null || objectName == null) {
                throw new ExecutionException("Bucket or object name not provided in resource properties.");
            }
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectName)
                    .build();
            ResponseInputStream<GetObjectResponse> s3Object = server.getS3Client().getObject(request);
            BufferedReader br = new BufferedReader(new InputStreamReader(s3Object, StandardCharsets.UTF_8));
            String headerLine = br.readLine();
            if (headerLine == null || headerLine.isEmpty()) {
                throw new ExecutionException("DigitalOcean Spaces CSV file has no header.");
            }
            List<Field> fields = parseHeader(headerLine);
            br.close();
            return fields;
        } catch (Exception e) {
            throw new ExecutionException("Failed to get fields from DigitalOcean Spaces CSV", e);
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

    private Reader getFileReader() throws ExecutionException {
        DigitalOceanReader reader = (DigitalOceanReader) this.getReader();
        var server = reader.getServer();
        String bucket = server.getBucket();
        String objectName = server.getObjectName();
        if (bucket == null || objectName == null) {
            throw new ExecutionException("Bucket or object name not provided in resource properties.");
        }
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectName)
                .build();
        try {
            ResponseInputStream<GetObjectResponse> s3Object = server.getS3Client().getObject(request);
            return new BufferedReader(new InputStreamReader(s3Object, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new ExecutionException("Failed to get file reader from DigitalOcean Spaces", e);
        }
    }

    @Override
    public DigitalOceanReader getReader() {
        return (DigitalOceanReader) super.getReader();
    }
}
