package com.everyarrow.dittoe.io.file.servers.ibm;

import com.everyarrow.dittoe.io.file.FileServer;
import com.everyarrow.dittoe.io.file.servers.ibm.reader.IbmReader;
import com.everyarrow.dittoe.io.file.servers.ibm.writer.IbmWriter;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import com.everyarrow.dittoe.utility.security.EndpointSafety;
import org.everyarrow.dittoe.schema.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;

/**
 * Concrete IBM COS (Cloud Object Storage) server.
 * <p>
 * This server creates an S3Client using IBM COS credentials and configuration provided in the resource
 * properties. The required keys are:
 * <ul>
 *   <li>ibmApiKey</li>
 *   <li>ibmServiceInstanceId</li>
 *   <li>ibmRegion</li>
 *   <li>ibmEndpointUrl</li>
 *   <li>ibmBucket</li>
 *   <li>ibmObject</li>
 * </ul>
 * The CSV delimiter (csvDelimiter) is optional (default is a comma).
 * All heavy initialization is performed in {@code init()}.
 * </p>
 */
public class IbmServer extends FileServer {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private S3Client s3Client;
    private String bucket;
    private String objectName;

    /**
     * Minimal constructor. No heavy initialization is done here.
     *
     * @param isSource true if this server is used as a source; false if used as a target.
     * @throws ExecutionException if instantiation fails.
     */
    public IbmServer(Boolean isSource) throws ExecutionException {
        super(isSource ? new IbmReader() : null, isSource ? null : new IbmWriter());
        if (isSource) {
            this.getReader().setServer(this);
        } else {
            this.getWriter().setServer(this);
        }
    }

    /**
     * Initializes the IBM COS server.
     * <p>
     * This method reads the following required properties from the resource:
     * <ul>
     *   <li>ibmApiKey</li>
     *   <li>ibmServiceInstanceId</li>
     *   <li>ibmRegion</li>
     *   <li>ibmEndpointUrl</li>
     *   <li>ibmBucket</li>
     *   <li>ibmObject</li>
     * </ul>
     * Then it creates the S3Client with an endpoint override.
     * </p>
     *
     * @throws ExecutionException if connection creation or initialization fails.
     */
    @Override
    public void init() throws ExecutionException {
        Resource resource = this.getResource();
        if (resource == null) {
            throw new ExecutionException("Resource is not set on the server.");
        }
        Map<String, String> props = resource.getResourceProperties();
        String apiKey = props.get("ibmApiKey");
        String serviceInstanceId = props.get("ibmServiceInstanceId");
        String regionStr = props.get("ibmRegion");
        String endpoint = props.get("ibmEndpointUrl");
        bucket = props.get("ibmBucket");
        objectName = props.get("ibmObject");
        if (apiKey == null || serviceInstanceId == null || regionStr == null ||
                endpoint == null || bucket == null || objectName == null) {
            throw new ExecutionException("Missing required IBM COS properties: ibmApiKey, ibmServiceInstanceId, ibmRegion, ibmEndpointUrl, ibmBucket, and ibmObject.");
        }
        try {
            AwsBasicCredentials awsCreds = AwsBasicCredentials.create(apiKey, serviceInstanceId);
            s3Client = S3Client.builder()
                    .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
                    .region(Region.of(regionStr))
                    .endpointOverride(EndpointSafety.requirePublicHttpsEndpoint(endpoint, props))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(true)
                            .build())
                    .build();
        } catch (Exception e) {
            throw new ExecutionException("Failed to create IBM COS client", e);
        }
        // Call parent and initialize reader/writer.
        super.init();
        if (this.getReader() != null) {
            this.getReader().init();
        }
        if (this.getWriter() != null) {
            this.getWriter().init();
        }
    }

    @Override
    public void testConnection() {
        try {
            s3Client.listBuckets();
        } catch (Exception e) {
            throw new RuntimeException("IBM COS connection test failed.", e);
        }
    }

    public File downloadFile(String fileName, Path path) throws ExecutionException {
        try {
            // Retrieve the bucket name from this server (set during initialization).
            String bucket = this.getBucket();
            if (bucket == null || bucket.isEmpty()) {
                throw new ExecutionException("Bucket name (ibmBucket) is not provided in resource properties.");
            }

            // Build the GetObjectRequest using the bucket and the provided file name as the object key.
            software.amazon.awssdk.services.s3.model.GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileName)
                    .build();

            // Download the object from IBM COS into the temporary file.
            this.getS3Client().getObject(getRequest, ResponseTransformer.toFile(path));

            // Return the downloaded file.
            return path.toFile();
        } catch (Exception e) {
            throw new ExecutionException("Failed to download file '" + fileName + "' from IBM COS", e);
        }
    }

    public Logger getLogger() {
        return logger;
    }

    /**
     * Returns the S3 client used for IBM COS.
     *
     * @return the S3Client instance.
     */
    public S3Client getS3Client() {
        return s3Client;
    }

    public String getBucket() {
        return bucket;
    }

    public String getObjectName() {
        return objectName;
    }

}
