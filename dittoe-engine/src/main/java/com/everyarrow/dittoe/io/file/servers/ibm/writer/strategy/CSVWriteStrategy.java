package com.everyarrow.dittoe.io.file.servers.ibm.writer.strategy;

import com.everyarrow.dittoe.io.WriteStrategy;
import com.everyarrow.dittoe.io.data.DataStoreException;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.io.file.servers.ibm.writer.IbmWriter;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.Field;
import org.everyarrow.dittoe.schema.Resource;
import org.everyarrow.dittoe.schema.SourceToTargetMapping;
import com.everyarrow.dittoe.utility.Batch;
import com.everyarrow.dittoe.utility.Chunk;
import com.everyarrow.dittoe.utility.metadata.MetadataRepositoryAPI;
import com.everyarrow.dittoe.utility.security.SafePathResolver;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Concrete CSV write strategy for IBM COS.
 * <p>
 * Data from the DataStore is written to a temporary CSV file and then uploaded to IBM COS.
 * The call to Batch.divideIntoChunks is wrapped in a try-catch block to handle DataStoreException.
 * </p>
 */
public class CSVWriteStrategy extends WriteStrategy {
    private String delimiter = ","; // default delimiter

    public CSVWriteStrategy() throws ExecutionException {
        super();
    }

    @Override
    public void init() throws ExecutionException {
        Resource resource = this.getWriter().getServer().getResource();
        Map<String, String> props = resource.getResourceProperties();
        delimiter = props.getOrDefault("csvDelimiter", ",");
        super.init();
    }

    @Override
    public void write() throws ExecutionException {
        try {
            DataStoreInterface dataStore = DataStoreInterface.getDataStoreInterface();
            long rowCount = dataStore.size();
            if (rowCount == 0) return;

            Resource resource = this.getWriter().getServer().getResource();
            IbmWriter writerInstance = (IbmWriter) this.getWriter();
            var server = writerInstance.getServer();
            String bucket = server.getBucket();
            String objectName = SafePathResolver.requireSafeRelativePath(server.getObjectName(),
                    "IBM COS object name");
            if (bucket == null || objectName == null) {
                throw new ExecutionException("Bucket or object name not provided in resource properties.");
            }

            Path tempFile = SafePathResolver.createTempFile("ibmcos", ".csv");
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

                // Upload the temporary CSV file to IBM COS.
                S3Client s3Client = server.getS3Client();
                PutObjectRequest putRequest = PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectName)
                        .build();
                s3Client.putObject(putRequest, RequestBody.fromFile(tempFile));
            } finally {
                Files.deleteIfExists(tempFile);
            }
            
            // Notify observers of successful write
            this.getWriter().notifyDataInsertionObservers(rowCount);
        } catch (IOException e) {
            throw new ExecutionException("Failed to write CSV data to temporary file for IBM COS upload", e);
        } catch (Exception e) {
            throw new ExecutionException("Failed to upload CSV file to IBM COS", e);
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
    public IbmWriter getWriter() {
        return (IbmWriter) super.getWriter();
    }
}
