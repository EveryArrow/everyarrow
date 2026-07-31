package com.everyarrow.dittoe.io.file.servers.oracle.writer.strategy;

import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.responses.PutObjectResponse;
import com.everyarrow.dittoe.io.WriteStrategy;
import com.everyarrow.dittoe.io.data.DataStoreException;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.io.file.servers.oracle.OracleServer;
import com.everyarrow.dittoe.io.file.servers.oracle.writer.OracleWriter;
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
 * Concrete CSV write strategy for Oracle Cloud Storage.
 * <p>
 * Data from the DataStore is written to a temporary CSV file and then uploaded to Oracle Cloud Storage.
 * Note: {@code Batch.divideIntoChunks} may throw a {@code DataStoreException} which is caught and wrapped.
 */
public class CSVWriteStrategy extends WriteStrategy {
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
            if (size == 0) return;

            Resource resource = this.getWriter().getServer().getResource();
            String objectName = ((OracleServer) this.getWriter().getServer()).getObjectName();
            if (objectName == null) {
                throw new ExecutionException("Oracle object name not provided in resource properties.");
            }

            Path tempFile = SafePathResolver.createTempFile("oracle", ".csv");
            try {
                try (BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                    List<SourceToTargetMapping> mappings = MetadataRepositoryAPI.getPipelineMetadata().getS2TMap();
                    writeHeaders(writer, mappings);
                    List<Chunk> chunks;
                    try {
                        chunks = Batch.divideIntoChunks(dataStore, 100000);
                    } catch (DataStoreException dse) {
                        throw new ExecutionException("DataStoreException occurred while dividing data into chunks", dse);
                    }
                    for (Chunk chunk : chunks) {
                        for (int i = chunk.startIndex(); i <= chunk.endIndex(); i++) {
                            Map<String, Object> rowData = dataStore.get(i);
                            String csvLine = getMappedData(rowData, mappings);
                            writer.write(csvLine);
                        }
                    }
                } catch (DataStoreException e) {
                    throw new RuntimeException(e);
                }

                // Upload the file to Oracle Cloud Storage.
                OracleWriter writerInstance = (OracleWriter) this.getWriter();
                OracleServer server = (OracleServer) writerInstance.getServer();
                ObjectStorageClient client = server.getObjectStorageClient();
                PutObjectRequest putRequest = PutObjectRequest.builder()
                        .namespaceName(server.getNamespace())
                        .bucketName(server.getBucketName())
                        .objectName(objectName)
                        .build();
                PutObjectResponse response = client.putObject(putRequest);
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (IOException e) {
            throw new ExecutionException("Failed to write CSV data to temporary file for Oracle upload", e);
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
    public OracleWriter getWriter() {
        return (OracleWriter) super.getWriter();
    }
}
