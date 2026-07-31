package com.everyarrow.dittoe.io.file.servers.oracle;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import com.everyarrow.dittoe.io.file.FileServer;
import com.everyarrow.dittoe.io.file.servers.oracle.reader.OracleReader;
import com.everyarrow.dittoe.io.file.servers.oracle.writer.OracleWriter;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * Concrete Oracle Cloud Storage server.
 * <p>
 * This server creates the Oracle Cloud Storage client using credentials from the resource properties
 * in its {@code init()} method. It uses an OracleReader when used as a source and an OracleWriter when used as a target.
 */
public class OracleServer extends FileServer {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private ObjectStorageClient objectStorageClient;
    private String namespace;   // Oracle Cloud Storage namespace
    private String bucketName;  // Bucket name
    private String objectName;  // Default object name (e.g., CSV file name)

    /**
     * Minimal constructor. No heavy initialization is performed here.
     *
     * @param isSource true if this server is used as a source; false if used as a target.
     * @throws ExecutionException if instantiation fails.
     */
    public OracleServer(Boolean isSource) throws ExecutionException {
        super(isSource ? new OracleReader() : null, isSource ? null : new OracleWriter());
        if (isSource) {
            this.getReader().setServer(this);
        } else {
            this.getWriter().setServer(this);
        }
    }

    /**
     * Initializes the Oracle Cloud Storage server.
     * <p>
     * This method reads the required credentials and configuration from the resource properties and
     * creates the Object Storage client.
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
        String tenancyId = props.get("oracleTenancy");
        String userId = props.get("oracleUser");
        String fingerprint = props.get("oracleFingerprint");
        String privateKey = props.get("oraclePrivateKey");
        String region = props.get("oracleRegion");
        this.namespace = props.get("oracleNamespace");
        this.bucketName = props.get("oracleBucket");
        this.objectName = props.get("oracleObject");

        if (tenancyId == null || userId == null || fingerprint == null ||
                privateKey == null || region == null || namespace == null ||
                bucketName == null || objectName == null) {
            throw new ExecutionException("Missing required Oracle Cloud Storage credentials or configuration in resource properties.");
        }
        try {
            SimpleAuthenticationDetailsProvider provider = SimpleAuthenticationDetailsProvider.builder()
                    .tenantId(tenancyId)
                    .userId(userId)
                    .fingerprint(fingerprint)
                    .privateKeySupplier(() -> new ByteArrayInputStream(privateKey.getBytes()))
                    .build();
            objectStorageClient = ObjectStorageClient.builder()
                    .region(Region.fromRegionId(region))
                    .build(provider);
        } catch (Exception e) {
            throw new ExecutionException("Failed to create Oracle Cloud Storage client", e);
        }
        // Call parent and subordinate initializations.
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
            // Test the connection by listing objects in the bucket.
            objectStorageClient.listObjects(
                    com.oracle.bmc.objectstorage.requests.ListObjectsRequest.builder()
                            .namespaceName(namespace)
                            .bucketName(bucketName)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Oracle Cloud Storage connection test failed.", e);
        }
    }

    public File downloadFile(String fileName, Path path) throws ExecutionException {
        try {
            // Ensure that the namespace and bucket name are available.
            if (this.getNamespace() == null || this.getBucketName() == null) {
                throw new ExecutionException("Namespace or bucket name is not configured.");
            }

            // Build the GetObjectRequest using the server's namespace, bucket, and the provided fileName as the object key.
            GetObjectRequest request = GetObjectRequest.builder()
                    .namespaceName(this.getNamespace())
                    .bucketName(this.getBucketName())
                    .objectName(fileName)
                    .build();

            // Use the existing ObjectStorageClient to download the object.
            GetObjectResponse response = this.getObjectStorageClient().getObject(request);

            // Copy the content from the response's InputStream to the temporary file.
            try (InputStream inputStream = response.getInputStream()) {
                Files.copy(inputStream, path, StandardCopyOption.REPLACE_EXISTING);
            }

            // Return the downloaded file.
            return path.toFile();
        } catch (Exception e) {
            throw new ExecutionException("Failed to download file '" + fileName + "' from Oracle Cloud Storage", e);
        }
    }

    public Logger getLogger() {
        return logger;
    }

    /**
     * Returns the Oracle Cloud Storage client.
     *
     * @return the ObjectStorageClient instance.
     */
    public ObjectStorageClient getObjectStorageClient() {
        return objectStorageClient;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getBucketName() {
        return bucketName;
    }

    public String getObjectName() {
        return objectName;
    }

}
