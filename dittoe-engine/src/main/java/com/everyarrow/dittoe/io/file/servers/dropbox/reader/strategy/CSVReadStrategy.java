package com.everyarrow.dittoe.io.file.servers.dropbox.reader.strategy;

import com.dropbox.core.DbxDownloader;
import com.dropbox.core.v2.files.FileMetadata;
import com.everyarrow.dittoe.io.ReadStrategy;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.io.file.servers.dropbox.reader.DropboxReader;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.DataType;
import org.everyarrow.dittoe.schema.Field;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Concrete CSV read strategy for Dropbox.
 * <p>
 * Reads a CSV file from Dropbox and populates the DataStore.
 */
public class CSVReadStrategy extends ReadStrategy {
    private static final String DELIMITER_OPTION = "csvDelimiter";
    private static final String FILE_PATH_PROPERTY = "dropboxFilePath";
    private String delimiter = ",";

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
            Map<String, String> props = this.getReader().getServer().getResource().getResourceProperties();
            String filePath = props.get(FILE_PATH_PROPERTY);
            if (filePath == null) {
                throw new ExecutionException("dropboxFilePath not provided in resource properties.");
            }
            DropboxReader reader = (DropboxReader) this.getReader();
            var server = reader.getServer();
            DbxDownloader<FileMetadata> downloader = server.getClient().files().download(filePath);
            InputStream inputStream = downloader.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }
            br.close();
            if (lines.isEmpty()) {
                throw new ExecutionException("Dropbox file is empty.");
            }
            // Parse header row into Field objects.
            String headerLine = lines.get(0);
            List<Field> fields = parseHeader(headerLine);
            if (this.getReader().getServer().getResource().getFields() == null ||
                    this.getReader().getServer().getResource().getFields().isEmpty()) {
                this.getReader().getServer().getResource().setFields(fields);
            }
            // Build header indices map.
            Map<String, Integer> headerIndices = new HashMap<>();
            String[] headers = parseAndTrim(headerLine, delimiter);
            for (int i = 0; i < headers.length; i++) {
                headerIndices.put(headers[i], i);
            }
            DataStoreInterface dataStore = DataStoreInterface.getDataStoreInterface();
            int rowNumber = 0;
            for (int i = 1; i < lines.size(); i++) {
                String dataLine = lines.get(i);
                if (!dataLine.isEmpty()) {
                    String[] parsedLine = parseAndTrim(dataLine, delimiter);
                    Map<String, Object> rowData = createDataMap(parsedLine, headerIndices);
                    dataStore.add(rowNumber++, rowData);
                }
            }
        } catch (Exception e) {
            throw new ExecutionException("Failed to read CSV from Dropbox", e);
        }
    }

    public void read(int limit) throws ExecutionException {
        try {
            Map<String, String> props = this.getReader().getServer().getResource().getResourceProperties();
            String filePath = props.get(FILE_PATH_PROPERTY);
            if (filePath == null) {
                throw new ExecutionException("dropboxFilePath not provided in resource properties.");
            }
            DropboxReader reader = (DropboxReader) this.getReader();
            var server = reader.getServer();
            DbxDownloader<FileMetadata> downloader = server.getClient().files().download(filePath);
            InputStream inputStream = downloader.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }
            br.close();
            if (lines.isEmpty()) {
                throw new ExecutionException("Dropbox file is empty.");
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
            throw new ExecutionException("Failed to read CSV from Dropbox (limited)", e);
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
            Map<String, String> props = this.getReader().getServer().getResource().getResourceProperties();
            String filePath = props.get(FILE_PATH_PROPERTY);
            if (filePath == null) {
                throw new ExecutionException("dropboxFilePath not provided in resource properties.");
            }
            DropboxReader reader = (DropboxReader) this.getReader();
            var server = reader.getServer();
            DbxDownloader<FileMetadata> downloader = server.getClient().files().download(filePath);
            InputStream inputStream = downloader.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
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
            throw new ExecutionException("Failed to count rows in Dropbox CSV", e);
        }
    }

    @Override
    public int getAverageRowSize() throws ExecutionException {
        try {
            Map<String, String> props = this.getReader().getServer().getResource().getResourceProperties();
            String filePath = props.get(FILE_PATH_PROPERTY);
            if (filePath == null) {
                throw new ExecutionException("dropboxFilePath not provided in resource properties.");
            }
            DropboxReader reader = (DropboxReader) this.getReader();
            var server = reader.getServer();
            DbxDownloader<FileMetadata> downloader = server.getClient().files().download(filePath);
            InputStream inputStream = downloader.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
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
            throw new ExecutionException("Failed to get average row size from Dropbox CSV", e);
        }
    }

    @Override
    public List<Field> getFields() throws ExecutionException {
        try {
            Map<String, String> props = this.getReader().getServer().getResource().getResourceProperties();
            String filePath = props.get(FILE_PATH_PROPERTY);
            if (filePath == null) {
                throw new ExecutionException("dropboxFilePath not provided in resource properties.");
            }
            DropboxReader reader = (DropboxReader) this.getReader();
            var server = reader.getServer();
            DbxDownloader<FileMetadata> downloader = server.getClient().files().download(filePath);
            InputStream inputStream = downloader.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            String headerLine = br.readLine();
            if (headerLine == null || headerLine.isEmpty()) {
                throw new ExecutionException("Dropbox CSV file has no header.");
            }
            List<Field> fields = parseHeader(headerLine);
            br.close();
            return fields;
        } catch (Exception e) {
            throw new ExecutionException("Failed to get fields from Dropbox CSV", e);
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
    public DropboxReader getReader() {
        return (DropboxReader) super.getReader();
    }
}
