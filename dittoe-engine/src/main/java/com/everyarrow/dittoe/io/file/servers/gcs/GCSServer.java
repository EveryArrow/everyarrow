package com.everyarrow.dittoe.io.file.servers.gcs;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.NoCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.everyarrow.dittoe.io.file.FileServer;
import com.everyarrow.dittoe.io.file.servers.gcs.reader.GCSReader;
import com.everyarrow.dittoe.io.file.servers.gcs.writer.GCSWriter;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import com.everyarrow.dittoe.utility.security.EndpointSafety;
import org.everyarrow.dittoe.schema.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

/**
 * Concrete GCS server that implements connection creation and server‐related functionalities.
 * <p>
 * All heavy initialization (including creating the GCS connection using credentials)
 * is performed in the {@code init()} method. This server uses a GCSReader when used as a source
 * and a GCSWriter when used as a target.
 */
public class GCSServer extends FileServer {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private Storage storage;

    /**
     * Minimal constructor. No heavy initialization is done here.
     * The reader and writer objects are created but not fully initialized.
     *
     * @param isSource true if the server is used as a source; false if used as a target.
     * @throws ExecutionException if instantiation fails.
     */
    public GCSServer(Boolean isSource) throws ExecutionException {
        // Pass minimal reader/writer objects to the parent constructor.
        super(isSource ? new GCSReader() : null, isSource ? null : new GCSWriter());
        if (isSource) {
            this.getReader().setServer(this);
        } else {
            this.getWriter().setServer(this);
        }
    }

    /**
     * Initializes the GCS server.
     * <p>
     * This method creates the GCS connection (client) using credentials and project ID from the resource properties.
     * The following properties must be defined in the resource:
     * <ul>
     *   <li>gcsProjectId</li>
     *   <li>gcsCredentialsJson</li>
     *   <li>gcsBucket</li>
     *   <li>gcsObject</li>
     * </ul>
     * After establishing the connection, it calls {@code init()} on the parent as well as on the associated reader and writer.
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
        String endpoint = props.get("gcsEndpoint");
        String projectId = props.getOrDefault("gcsProjectId", "dittoe-test");
        String credentialsJson = props.get("gcsCredentialsJson");
        if ((projectId == null || projectId.isBlank()) && (endpoint == null || endpoint.isBlank())) {
            throw new ExecutionException("Missing required GCS project ID in resource properties. Please set gcsProjectId.");
        }
        if ((credentialsJson == null || credentialsJson.isBlank()) && (endpoint == null || endpoint.isBlank())) {
            throw new ExecutionException("Missing required GCS credentials in resource properties. Please set gcsCredentialsJson.");
        }
        try {
            StorageOptions.Builder builder = StorageOptions.newBuilder()
                    .setProjectId(projectId);

            if (endpoint != null && !endpoint.isBlank()) {
                builder.setHost(validateEndpointOverride(props));
            }

            if (credentialsJson != null && !credentialsJson.isBlank()) {
                ByteArrayInputStream credentialsStream = new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8));
                builder.setCredentials(ServiceAccountCredentials.fromStream(credentialsStream));
            } else {
                builder.setCredentials(NoCredentials.getInstance());
            }

            this.storage = builder.build().getService();
        } catch (Exception e) {
            throw new ExecutionException("Failed to create GCS connection using provided credentials", e);
        }
        // Perform parent initialization.
        super.init();
        // Initialize reader and writer.
        if (this.getReader() != null) {
            this.getReader().init();
        }
        if (this.getWriter() != null) {
            this.getWriter().init();
        }
    }

    static String validateEndpointOverride(Map<String, String> properties) throws ExecutionException {
        String endpoint = properties.get("gcsEndpoint");
        if (endpoint == null || endpoint.isBlank()) {
            return null;
        }
        return EndpointSafety.requirePublicHttpsEndpoint(endpoint, properties).toString();
    }

    @Override
    public void testConnection() {
        try {
            // Simple test: list buckets (this may require proper permissions).
            storage.list();
        } catch (Exception e) {
            throw new RuntimeException("GCS connection test failed.", e);
        }
    }

    public File downloadFile(String fileName, Path path) throws ExecutionException {
        try {
            // Retrieve the bucket name from the resource properties.
            Map<String, String> props = this.getResource().getResourceProperties();
            String bucket = props.get("gcsBucket");
            if (bucket == null || bucket.isEmpty()) {
                throw new ExecutionException("Bucket name (gcsBucket) not provided in resource properties.");
            }

            // Construct the BlobId using the bucket and the provided file name (object key).
            BlobId blobId = BlobId.of(bucket, fileName);
            Blob blob = this.getStorage().get(blobId);
            if (blob == null) {
                throw new ExecutionException("No object found in bucket '" + bucket + "' with key '" + fileName + "'.");
            }

            // Download the blob's content to the temporary file.
            blob.downloadTo(path);

            // Return the downloaded file.
            return path.toFile();
        } catch (Exception e) {
            throw new ExecutionException("Failed to download file '" + fileName + "' from GCS", e);
        }
    }

    public Logger getLogger() {
        return logger;
    }

    /**
     * Provides the GCS Storage client created during initialization.
     *
     * @return the Storage instance.
     */
    public Storage getStorage() {
        return storage;
    }

}
