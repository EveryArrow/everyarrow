package com.everyarrow.dittoe.io.file.servers.oracle.reader.strategy;

import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import com.everyarrow.dittoe.io.ReadStrategy;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.io.file.servers.oracle.reader.OracleReader;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.DataType;
import org.everyarrow.dittoe.schema.Field;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Concrete CSV read strategy for Oracle Cloud Storage.
 * <p>
 * Reads a CSV file from Oracle Object Storage and populates the DataStore.
 */
public class CSVReadStrategy extends ReadStrategy {
    private static final String DELIMITER_OPTION = "csvDelimiter";
    private String delimiter = ","; // default delimiter

    public CSVReadStrategy() throws ExecutionException {
        super();
    }

    @Override
    public void init() throws ExecutionException {
        Map<String, String> props = this.getReader().getServer().getResource().getResourceProperties();
        this.delimiter = props.getOrDefault(DELIMITER_OPTION, ",");
        super.init();
    }

    @Override
    public void read() throws ExecutionException {
        try {
            OracleReader oracleReader = (OracleReader) this.getReader();
            var server = oracleReader.getServer();
            String namespace = server.getNamespace();
            String bucketName = server.getBucketName();
            String objectName = server.getObjectName();
            if (namespace == null || bucketName == null || objectName == null) {
                throw new ExecutionException("Missing Oracle Cloud Storage parameters in resource properties.");
            }
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .namespaceName(namespace)
                    .bucketName(bucketName)
                    .objectName(objectName)
                    .build();
            GetObjectResponse response = server.getObjectStorageClient().getObject(getObjectRequest);
            BufferedReader reader = new BufferedReader(new InputStreamReader(response.getInputStream(), StandardCharsets.UTF_8));

            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }
            reader.close();
            if (lines.isEmpty()) {
                throw new ExecutionException("Oracle Cloud Storage CSV file is empty.");
            }
            // Parse header row into Field objects.
            String headerLine = lines.get(0);
            List<Field> fields = parseHeader(headerLine);
            if (this.getReader().getServer().getResource().getFields() == null ||
                    this.getReader().getServer().getResource().getFields().isEmpty()) {
                this.getReader().getServer().getResource().setFields(fields);
            }
            // Build header index map.
            Map<String, Integer> headerIndices = new HashMap<>();
            String[] headers = parseAndTrim(headerLine, delimiter);
            for (int i = 0; i < headers.length; i++) {
                headerIndices.put(headers[i], i);
            }
            DataStoreInterface dataStore = DataStoreInterface.getDataStoreInterface();
            int rowNumber = 0;
            // Process each data line (skip header).
            for (int i = 1; i < lines.size(); i++) {
                String dataLine = lines.get(i);
                if (!dataLine.isEmpty()) {
                    String[] parsedLine = parseAndTrim(dataLine, delimiter);
                    Map<String, Object> rowData = createDataMap(parsedLine, headerIndices);
                    dataStore.add(rowNumber++, rowData);
                }
            }
        } catch (Exception e) {
            throw new ExecutionException("Failed to read CSV from Oracle Cloud Storage", e);
        }
    }

    public void read(int limit) throws ExecutionException {
        try {
            OracleReader oracleReader = (OracleReader) this.getReader();
            var server = oracleReader.getServer();
            String namespace = server.getNamespace();
            String bucketName = server.getBucketName();
            String objectName = server.getObjectName();
            if (namespace == null || bucketName == null || objectName == null) {
                throw new ExecutionException("Missing Oracle Cloud Storage parameters in resource properties.");
            }
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .namespaceName(namespace)
                    .bucketName(bucketName)
                    .objectName(objectName)
                    .build();
            GetObjectResponse response = server.getObjectStorageClient().getObject(getObjectRequest);
            BufferedReader reader = new BufferedReader(new InputStreamReader(response.getInputStream(), StandardCharsets.UTF_8));

            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }
            reader.close();
            if (lines.isEmpty()) {
                throw new ExecutionException("Oracle Cloud Storage CSV file is empty.");
            }
            String headerLine = lines.get(0);
            List<Field> fields = parseHeader(headerLine);
            if (this.getReader().getServer().getResource().getFields() == null ||
                    this.getReader().getServer().getResource().getFields().isEmpty()) {
                this.getReader().getServer().getResource().setFields(fields);
            }
            Map<String, Integer> headerIndices = new HashMap<>();
            String[] headers = parseAndTrim(headerLine, delimiter);
            for (int i = 0; i < headers.length; i++) {
                headerIndices.put(headers[i], i);
            }
            DataStoreInterface dataStore = DataStoreInterface.getDataStoreInterface();
            int rowNumber = 0;
            for (int i = 1; i < lines.size() && rowNumber < limit; i++) {
                String dataLine = lines.get(i);
                if (!dataLine.isEmpty()) {
                    String[] parsedLine = parseAndTrim(dataLine, delimiter);
                    Map<String, Object> rowData = createDataMap(parsedLine, headerIndices);
                    dataStore.add(rowNumber++, rowData);
                }
            }
        } catch (Exception e) {
            throw new ExecutionException("Failed to read CSV from Oracle Cloud Storage (limited)", e);
        }
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
            OracleReader oracleReader = (OracleReader) this.getReader();
            var server = oracleReader.getServer();
            String namespace = server.getNamespace();
            String bucketName = server.getBucketName();
            String objectName = server.getObjectName();
            if (namespace == null || bucketName == null || objectName == null) {
                throw new ExecutionException("Missing Oracle Cloud Storage parameters in resource properties.");
            }
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .namespaceName(namespace)
                    .bucketName(bucketName)
                    .objectName(objectName)
                    .build();
            GetObjectResponse response = server.getObjectStorageClient().getObject(getObjectRequest);
            BufferedReader reader = new BufferedReader(new InputStreamReader(response.getInputStream(), StandardCharsets.UTF_8));
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
            throw new ExecutionException("Failed to count rows in Oracle Cloud Storage CSV", e);
        }
    }

    @Override
    public int getAverageRowSize() throws ExecutionException {
        try {
            OracleReader oracleReader = (OracleReader) this.getReader();
            var server = oracleReader.getServer();
            String namespace = server.getNamespace();
            String bucketName = server.getBucketName();
            String objectName = server.getObjectName();
            if (namespace == null || bucketName == null || objectName == null) {
                throw new ExecutionException("Missing Oracle Cloud Storage parameters in resource properties.");
            }
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .namespaceName(namespace)
                    .bucketName(bucketName)
                    .objectName(objectName)
                    .build();
            GetObjectResponse response = server.getObjectStorageClient().getObject(getObjectRequest);
            BufferedReader reader = new BufferedReader(new InputStreamReader(response.getInputStream(), StandardCharsets.UTF_8));
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
            throw new ExecutionException("Failed to get average row size from Oracle Cloud Storage CSV", e);
        }
    }

    @Override
    public List<Field> getFields() throws ExecutionException {
        try {
            OracleReader oracleReader = (OracleReader) this.getReader();
            var server = oracleReader.getServer();
            String namespace = server.getNamespace();
            String bucketName = server.getBucketName();
            String objectName = server.getObjectName();
            if (namespace == null || bucketName == null || objectName == null) {
                throw new ExecutionException("Missing Oracle Cloud Storage parameters in resource properties.");
            }
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .namespaceName(namespace)
                    .bucketName(bucketName)
                    .objectName(objectName)
                    .build();
            GetObjectResponse response = server.getObjectStorageClient().getObject(getObjectRequest);
            BufferedReader reader = new BufferedReader(new InputStreamReader(response.getInputStream(), StandardCharsets.UTF_8));
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isEmpty()) {
                throw new ExecutionException("Oracle Cloud Storage CSV file has no header.");
            }
            List<Field> fields = parseHeader(headerLine);
            reader.close();
            return fields;
        } catch (Exception e) {
            throw new ExecutionException("Failed to get fields from Oracle Cloud Storage CSV", e);
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
    public OracleReader getReader() {
        return (OracleReader) super.getReader();
    }
}
