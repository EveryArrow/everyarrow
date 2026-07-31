package com.everyarrow.dittoe.io.file.servers.gcs.writer.strategy;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.WriteChannel;
import com.everyarrow.dittoe.io.WriteStrategy;
import com.everyarrow.dittoe.io.data.DataStoreException;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.io.file.servers.gcs.GCSServer;
import com.everyarrow.dittoe.io.file.servers.gcs.writer.GCSWriter;
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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Concrete CSV write strategy that writes CSV data to Google Cloud Storage.
 * <p>
 * Data from the DataStore is written to a temporary CSV file and then uploaded to GCS.
 */
public class CSVWriteStrategy extends WriteStrategy {
    private static final String BUCKET_PROPERTY = "gcsBucket";
    private static final String OBJECT_PROPERTY = "gcsObject";
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

            // Retrieve GCS parameters from resource properties.
            Resource resource = this.getWriter().getServer().getResource();
            Map<String, String> props = resource.getResourceProperties();
            String bucket = props.get(BUCKET_PROPERTY);
            String objectName = props.get(OBJECT_PROPERTY);
            if (bucket == null || objectName == null) {
                throw new ExecutionException("GCS bucket or object name not provided in resource properties.");
            }

            // Create a temporary file to write CSV data.
            Path tempFile = SafePathResolver.createTempFile("gcs", ".csv");
            try {
                try (BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                    // Write CSV headers from the source-to-target mapping.
                    List<SourceToTargetMapping> mappings = MetadataRepositoryAPI.getPipelineMetadata().getS2TMap();
                    writeHeaders(writer, mappings);
                    // Write data rows.
                    List<Chunk> chunks = Batch.divideIntoChunks(dataStore, 100000);
                    for (Chunk chunk : chunks) {
                        for (int i = chunk.startIndex(); i <= chunk.endIndex(); i++) {
                            Map<String, Object> rowData = dataStore.get(i);
                            String csvLine = getMappedData(rowData, mappings);
                            writer.write(csvLine);
                        }
                    }
                }

                // Upload the temporary file to GCS.
                Storage storage = ((GCSServer) this.getWriter().getServer()).getStorage();
                BlobId blobId = BlobId.of(bucket, objectName);
                BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("text/csv").build();
                try (WriteChannel writer = storage.writer(blobInfo);
                     InputStream inputStream = Files.newInputStream(tempFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        writer.write(ByteBuffer.wrap(buffer, 0, bytesRead));
                    }
                }
            } finally {
                Files.deleteIfExists(tempFile);
            }
            this.getWriter().notifyDataInsertionObservers(size);
        } catch (IOException | DataStoreException e) {
            throw new ExecutionException("Failed to write data to GCS", e);
        }
    }

    /**
     * Writes CSV headers based on source-to-target mappings.
     */
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

    /**
     * Applies source-to-target mapping to a data row to create a CSV-formatted line.
     */
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
    public GCSWriter getWriter() {
        return (GCSWriter) super.getWriter();
    }
}
