package com.everyarrow.dittoe.io.file.servers.s3.writer.strategy;

import com.everyarrow.dittoe.io.WriteStrategy;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.io.file.servers.s3.writer.S3Writer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.PipelineMetadata;
import org.everyarrow.dittoe.schema.Resource;
import com.everyarrow.dittoe.utility.CsvUtils;
import com.everyarrow.dittoe.utility.metadata.MetadataRepositoryAPI;
import com.everyarrow.dittoe.utility.security.SafePathResolver;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Concrete write strategy that writes CSV data to S3.
 * <p>
 * Data from the DataStore is written to a temporary CSV file and then uploaded to S3.
 */
public class CSVWriteStrategy extends WriteStrategy {
    private static final String BUCKET_PROPERTY = "bucket";
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
            PipelineMetadata pipelineMetadata = MetadataRepositoryAPI.getPipelineMetadata();

            long rowCount = dataStore.size();
            if (rowCount == 0) {
                return;
            }

            // Retrieve S3 parameters from resource properties.
            Resource resource = this.getWriter().getServer().getResource();
            Map<String, String> props = resource.getResourceProperties();
            String bucket = props.get(BUCKET_PROPERTY);
            String key = SafePathResolver.requireSafeRelativePath(
                    this.getWriter().getServer().getResource().getResourceName(), "S3 key");
            if (bucket == null || key == null) {
                throw new ExecutionException("S3 bucket or key not provided in resource properties.");
            }

            Path tempFile = SafePathResolver.createTempFile("s3", ".csv");
            try {
                CsvUtils.write(new FileWriter(tempFile.toFile(), true), dataStore, pipelineMetadata);

                S3Client s3 = this.getWriter().getServer().getS3Client();
                PutObjectRequest putReq = PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build();
                s3.putObject(putReq, RequestBody.fromFile(tempFile));

                // Notify observers of successful write
                this.getWriter().notifyDataInsertionObservers(rowCount);
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (IllegalArgumentException | IOException e) {
            throw new ExecutionException("Failed to write data to S3", e);
        }
    }


    @Override
    public S3Writer getWriter() {
        return (S3Writer) super.getWriter();
    }
}
