package com.everyarrow.dittoe.io.file.servers.digitalocean;

import com.everyarrow.dittoe.io.file.FileServer;
import com.everyarrow.dittoe.io.file.servers.digitalocean.reader.DigitalOceanReader;
import com.everyarrow.dittoe.io.file.servers.digitalocean.writer.DigitalOceanWriter;
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
 * Concrete DigitalOcean Spaces server.
 * <p>
 * This server creates an S3Client using DigitalOcean Spaces credentials and configuration provided in the resource
 * properties. Required keys are:
 * <ul>
 *   <li>doSpacesAccessKey</li>
 *   <li>doSpacesSecretKey</li>
 *   <li>doSpacesRegion</li>
 *   <li>doSpacesEndpoint</li>
 *   <li>doSpacesBucket</li>
 *   <li>doSpacesObject</li>
 * </ul>
 * The CSV delimiter (csvDelimiter) is optional (default is a comma). All heavy initialization is done in {@code init()}.
 * </p>
 */
public class DigitalOceanServer extends FileServer {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private S3Client s3Client;
    private String bucket;
    private String objectName;

    /**
     * Minimal constructor. No heavy initialization here.
     *
     * @param isSource true if used as a source; false if used as a target.
     * @throws ExecutionException if instantiation fails.
     */
    public DigitalOceanServer(Boolean isSource) throws ExecutionException {
        super(isSource ? new DigitalOceanReader() : null, isSource ? null : new DigitalOceanWriter());
        if (isSource) {
            this.getReader().setServer(this);
        } else {
            this.getWriter().setServer(this);
        }
    }

    /**
     * Initializes the DigitalOcean Spaces server.
     * <p>
     * Reads required properties from the resource:
     * <ul>
     *   <li>doSpacesAccessKey</li>
     *   <li>doSpacesSecretKey</li>
     *   <li>doSpacesRegion</li>
     *   <li>doSpacesEndpoint</li>
     *   <li>doSpacesBucket</li>
     *   <li>doSpacesObject</li>
     * </ul>
     * Then creates the S3 client with an endpoint override.
     * </p>
     *
     * @throws ExecutionException if connection creation fails.
     */
    @Override
    public void init() throws ExecutionException {
        Resource resource = this.getResource();
        if (resource == null) {
            throw new ExecutionException("Resource is not set on the server.");
        }
        Map<String, String> props = resource.getResourceProperties();
        String accessKey = props.get("doSpacesAccessKey");
        String secretKey = props.get("doSpacesSecretKey");
        String regionStr = props.get("doSpacesRegion");
        String endpoint = props.get("doSpacesEndpoint");
        bucket = props.get("doSpacesBucket");
        objectName = props.get("doSpacesObject");
        if (accessKey == null || secretKey == null || regionStr == null ||
                endpoint == null || bucket == null || objectName == null) {
            throw new ExecutionException("Missing required DigitalOcean Spaces properties: doSpacesAccessKey, doSpacesSecretKey, doSpacesRegion, doSpacesEndpoint, doSpacesBucket, and doSpacesObject.");
        }
        try {
            AwsBasicCredentials awsCreds = AwsBasicCredentials.create(accessKey, secretKey);
            s3Client = S3Client.builder()
                    .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
                    .region(Region.of(regionStr))
                    .endpointOverride(EndpointSafety.requirePublicHttpsEndpoint(endpoint, props))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(true)
                            .build())
                    .build();
        } catch (Exception e) {
            throw new ExecutionException("Failed to create DigitalOcean Spaces client", e);
        }
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
            throw new RuntimeException("DigitalOcean Spaces connection test failed.", e);
        }
    }

    @Override
    public File downloadFile(String fileName, Path path) throws ExecutionException {
        try {
            // Retrieve the bucket name from the resource properties.
            Map<String, String> props = this.getResource().getResourceProperties();
            String bucket = props.get("doSpacesBucket");
            if (bucket == null || bucket.isEmpty()) {
                throw new ExecutionException("Bucket name (doSpacesBucket) not provided in resource properties.");
            }

            // Build the GetObjectRequest using the bucket and the provided fileName as the object key.
            software.amazon.awssdk.services.s3.model.GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileName)
                    .build();

            // Download the object from DigitalOcean Spaces into the temporary file.
            this.getS3Client().getObject(getRequest, ResponseTransformer.toFile(path));

            // Return the downloaded file.
            return path.toFile();
        } catch (Exception e) {
            throw new ExecutionException("Failed to download file '" + fileName + "' from DigitalOcean Spaces", e);
        }
    }

    public Logger getLogger() {
        return logger;
    }

    /**
     * Returns the S3 client.
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
