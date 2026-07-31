package com.everyarrow.dittoe.io.file.servers.googledrive.writer.strategy;

import com.everyarrow.dittoe.io.file.servers.googledrive.GoogleDriveServer;
import com.google.api.client.http.InputStreamContent;
import com.google.api.services.drive.Drive;
import com.everyarrow.dittoe.io.WriteStrategy;
import com.everyarrow.dittoe.io.data.DataStoreException;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.io.file.servers.googledrive.writer.GoogleDriveWriter;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.Field;
import org.everyarrow.dittoe.schema.Resource;
import org.everyarrow.dittoe.schema.SourceToTargetMapping;
import com.everyarrow.dittoe.utility.Batch;
import com.everyarrow.dittoe.utility.Chunk;
import com.everyarrow.dittoe.utility.metadata.MetadataRepositoryAPI;
import com.everyarrow.dittoe.utility.security.SafePathResolver;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class CSVWriteStrategy extends WriteStrategy {
    private static final String DELIMITER_OPTION = "csvDelimiter";
    private String delimiter = ","; // default

    public CSVWriteStrategy() throws ExecutionException {
        super();
    }

    @Override
    public void init() throws ExecutionException {
        Map<String, String> props = this.getWriter().getServer().getResource().getResourceProperties();
        this.delimiter = props.getOrDefault(DELIMITER_OPTION, ",");
        super.init();
    }

    @Override
    public void write() throws ExecutionException {
        try {
            DataStoreInterface dataStore = DataStoreInterface.getDataStoreInterface();
            int size = dataStore.size();
            if (size == 0) return;
            Resource resource = this.getWriter().getServer().getResource();
            String fileId = ((GoogleDriveServer) this.getWriter().getServer()).getFileId();
            if (fileId == null) {
                throw new ExecutionException("Google Drive fileId not provided in resource properties.");
            }
            // Write CSV data to a temporary file.
            Path tempFile = SafePathResolver.createTempFile("gdrive", ".csv");
            try {
                try (BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                    List<SourceToTargetMapping> mappings = MetadataRepositoryAPI.getPipelineMetadata().getS2TMap();
                    writeHeaders(writer, mappings);
                    List<Chunk> chunks;
                    try {
                        chunks = Batch.divideIntoChunks(dataStore, 100000);
                    } catch (DataStoreException dse) {
                        throw new ExecutionException("DataStoreException while dividing data into chunks", dse);
                    }
                    for (Chunk chunk : chunks) {
                        for (int i = chunk.startIndex(); i <= chunk.endIndex(); i++) {
                            Map<String, Object> rowData = dataStore.get(i);
                            String csvLine = getMappedData(rowData, mappings);
                            writer.write(csvLine);
                        }
                    }
                }

                // Update the Google Drive file content.
                GoogleDriveWriter writerInstance = (GoogleDriveWriter) this.getWriter();
                var server = ((GoogleDriveServer) writerInstance.getServer());
                Drive driveService = server.getDriveService();
                try (InputStream inputStream = Files.newInputStream(tempFile)) {
                    InputStreamContent mediaContent = new InputStreamContent("text/csv", inputStream);
                    mediaContent.setLength(Files.size(tempFile));
                    driveService.files().update(server.getFileId(), null, mediaContent).execute();
                }
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (IOException e) {
            throw new ExecutionException("Failed to write CSV data to temporary file for Google Drive upload", e);
        } catch (Exception e) {
            throw new ExecutionException("Failed to upload CSV file to Google Drive", e);
        }
    }

    private void writeHeaders(BufferedWriter writer, List<SourceToTargetMapping> mappings) throws IOException {
        for (int i = 0; i < mappings.size(); i++) {
            SourceToTargetMapping mapping = mappings.get(i);
            Field targetField = mapping.getTargetField();
            writer.write(targetField.getName());
            if (i < mappings.size() - 1) {
                writer.write(delimiter);
            }
        }
        writer.newLine();
    }

    private String getMappedData(Map<String, Object> rowData, List<SourceToTargetMapping> mappings) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mappings.size(); i++) {
            SourceToTargetMapping mapping = mappings.get(i);
            Object value = rowData.get(mapping.getSourceField().getName());
            String strVal = (value != null) ? value.toString() : "";
            if (strVal.contains(delimiter)) {
                strVal = "\"" + strVal + "\"";
            }
            sb.append(strVal);
            if (i < mappings.size() - 1) {
                sb.append(delimiter);
            }
        }
        sb.append(System.lineSeparator());
        return sb.toString();
    }

    @Override
    public GoogleDriveWriter getWriter() {
        return (GoogleDriveWriter) super.getWriter();
    }
}
