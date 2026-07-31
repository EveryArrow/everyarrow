package com.everyarrow.dittoe.io.file.servers.s3.reader.strategy;

import com.everyarrow.dittoe.io.ReadStrategy;
import com.everyarrow.dittoe.io.file.servers.s3.reader.S3Reader;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.Field;
import com.everyarrow.dittoe.utility.CsvUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Concrete read strategy that reads a CSV file stored in S3.
 */
public class CSVReadStrategy extends ReadStrategy {
    private static final String BUCKET_PROPERTY = "bucket";
    private static final String DELIMITER_OPTION = "csvDelimiter";
    private String delimiter = ","; // default delimiter

    public CSVReadStrategy() throws ExecutionException {
        super();
    }

    @Override
    public void init() throws ExecutionException {
        // Retrieve delimiter from the resource properties.
        Map<String, String> props = this.getReader().getServer().getResource().getResourceProperties();
        this.delimiter = props.getOrDefault(DELIMITER_OPTION, ",");
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
        return CsvUtils.getRange(getFileReader(), this.getReader().getServer().getResource().getFields(), this.getReader().getServer().getResource().getResourceProperties());
    }

    @Override
    public long getDataCount() throws ExecutionException {
        return CsvUtils.getDataCount(getFileReader(), this.getReader().getServer().getResource().getFields(), this.getReader().getServer().getResource().getResourceProperties());
    }

    @Override
    public int getAverageRowSize() throws ExecutionException {
        return CsvUtils.getAverageRowSize(getFileReader(),
                this.getReader().getServer().getResource().getFields());
    }

    @Override
    public List<Field> getFields() throws ExecutionException {
        return CsvUtils.getFields(getFileReader());
    }


    private Reader getFileReader() {
        Map<String, String> props = this.getReader().getServer().getResource().getResourceProperties();
        String bucket = props.get(BUCKET_PROPERTY);
        String key = this.getReader().getServer().getResource().getResourceName();
        if (bucket == null || key == null) {
            throw new RuntimeException("S3 bucket or key not provided in resource properties.");
        }

        S3Client s3 = this.getReader().getServer().getS3Client();
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        ResponseInputStream<GetObjectResponse> s3Object =
                s3.getObject(getObjectRequest, ResponseTransformer.toInputStream());

        return new InputStreamReader(s3Object, StandardCharsets.UTF_8);
    }


    @Override
    public S3Reader getReader() {
        return (S3Reader) super.getReader();
    }
}
