package com.everyarrow.dittoe.io.file.servers.azure.writer.strategy;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.everyarrow.dittoe.io.file.servers.azure.AzureServer;
import com.everyarrow.dittoe.io.WriteStrategy;
import com.everyarrow.dittoe.io.data.DataStoreException;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.io.file.servers.azure.writer.AzureWriter;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Concrete CSV write strategy that writes CSV data to Azure Blob Storage.
 * <p>
 * Data from the DataStore is written to a temporary CSV file and then uploaded to Azure Blob.
 */
public class CSVWriteStrategy extends WriteStrategy {
    private static final String BLOB_PROPERTY = "azureBlob";
    private static final String DELIMITER_OPTION = "csvDelimiter";
    private String delimiter = ","; // default delimiter

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
            if (size == 0) {
                return;
            }
            Resource resource = this.getWriter().getServer().getResource();
            Map<String, String> props = resource.getResourceProperties();
            String blobName = props.get(BLOB_PROPERTY);
            if (blobName == null) {
                throw new ExecutionException("Azure blob name not provided in resource properties.");
            }
            Path tempFile = SafePathResolver.createTempFile("azure", ".csv");
            try {
                try (BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                    List<SourceToTargetMapping> mappings = MetadataRepositoryAPI.getPipelineMetadata().getS2TMap();
                    writeHeaders(writer, mappings);
                    List<Chunk> chunks = Batch.divideIntoChunks(dataStore, 100000);
                    for (Chunk chunk : chunks) {
                        for (int i = chunk.startIndex(); i <= chunk.endIndex(); i++) {
                            Map<String, Object> rowData = dataStore.get(i);
                            String csvLine = getMappedData(rowData, mappings);
                            writer.write(csvLine);
                        }
                    }
                }
                // Upload the temporary file to Azure Blob.
                BlobContainerClient containerClient = ((AzureServer) this.getWriter().getServer()).getContainerClient();
                BlobClient blobClient = containerClient.getBlobClient(blobName);
                blobClient.uploadFromFile(tempFile.toString(), true);
            } finally {
                Files.deleteIfExists(tempFile);
            }
            this.getWriter().notifyDataInsertionObservers(size);
        } catch (IOException | DataStoreException e) {
            throw new ExecutionException("Failed to write data to Azure Blob Storage", e);
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
    public AzureWriter getWriter() {
        return (AzureWriter) super.getWriter();
    }
}
