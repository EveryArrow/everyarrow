package com.everyarrow.dittoe.io.file.servers.dropbox.writer.strategy;

import com.dropbox.core.v2.files.WriteMode;
import com.everyarrow.dittoe.io.WriteStrategy;
import com.everyarrow.dittoe.io.data.DataStoreException;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.io.file.servers.dropbox.writer.DropboxWriter;
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
    private static final String FILE_PATH_PROPERTY = "dropboxFilePath";
    private String delimiter = ",";

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
            String filePath = resource.getResourceProperties().get(FILE_PATH_PROPERTY);
            if (filePath == null) {
                throw new ExecutionException("dropboxFilePath not provided in resource properties.");
            }
            Path tempFile = SafePathResolver.createTempFile("dropbox", ".csv");
            try {
                try (BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                    List<SourceToTargetMapping> mappings = MetadataRepositoryAPI.getPipelineMetadata().getS2TMap();
                    writeHeaders(writer, mappings);
                    List<Chunk> chunks;
                    try {
                        chunks = Batch.divideIntoChunks(dataStore, 100000);
                    } catch (DataStoreException dse) {
                        throw new ExecutionException("Error dividing data into chunks", dse);
                    }
                    for (Chunk chunk : chunks) {
                        for (int i = chunk.startIndex(); i <= chunk.endIndex(); i++) {
                            Map<String, Object> rowData = dataStore.get(i);
                            String csvLine = getMappedData(rowData, mappings);
                            writer.write(csvLine);
                        }
                    }
                }
                // Upload the file to Dropbox.
                DropboxWriter writerInstance = (DropboxWriter) this.getWriter();
                var server = writerInstance.getServer();
                try (InputStream inputStream = Files.newInputStream(tempFile)) {
                    server.getClient().files().uploadBuilder(filePath)
                            .withMode(WriteMode.OVERWRITE)
                            .uploadAndFinish(inputStream);
                }
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (IOException e) {
            throw new ExecutionException("Failed to write CSV to temporary file for Dropbox upload", e);
        } catch (Exception e) {
            throw new ExecutionException("Failed to upload CSV file to Dropbox", e);
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
    public DropboxWriter getWriter() {
        return (DropboxWriter) super.getWriter();
    }
}
