package com.everyarrow.dittoe.io.file.servers.azure;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.everyarrow.dittoe.io.file.FileServer;
import com.everyarrow.dittoe.io.file.servers.azure.reader.AzureReader;
import com.everyarrow.dittoe.io.file.servers.azure.writer.AzureWriter;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;

/**
 * Concrete Azure server for Azure Blob Storage.
 * <p>
 * All heavy initialization (including connection creation using credentials) is performed in the {@code init()} method.
 * This server uses an AzureReader when used as a source and an AzureWriter when used as a target.
 */
public class AzureServer extends FileServer {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private BlobContainerClient containerClient;

    /**
     * Minimal constructor. No heavy initialization is done here.
     * The reader and writer objects are created but not fully initialized.
     *
     * @param isSource true if this server is used as a source; false if used as a target.
     * @throws ExecutionException if instantiation fails.
     */
    public AzureServer(Boolean isSource) throws ExecutionException {
        // Pass minimal reader/writer objects to the parent constructor.
        super(isSource ? new AzureReader() : null, isSource ? null : new AzureWriter());
        if (isSource) {
            this.getReader().setServer(this);
        } else {
            this.getWriter().setServer(this);
        }
    }

    /**
     * Initializes the Azure server.
     * <p>
     * This method creates the Azure Blob Storage connection using credentials and container information from the resource properties.
     * The following properties must be defined in the resource:
     * <ul>
     *   <li>azureAccountName</li>
     *   <li>azureAccountKey</li>
     *   <li>azureContainer</li>
     *   <li>azureBlob</li>
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
        String accountName = props.get("azureAccountName");
        String accountKey = props.get("azureAccountKey");
        String containerName = props.get("azureContainer");
        String connectionString = props.get("azureConnectionString");
        if ((connectionString == null || connectionString.isBlank()) && (accountName == null || accountKey == null)) {
            throw new ExecutionException("Missing required Azure credentials in resource properties. " +
                "Please set azureConnectionString or both azureAccountName and azureAccountKey.");
        }
        if (containerName == null) {
            throw new ExecutionException("Missing required Azure container in resource properties. Please set azureContainer.");
        }
        try {
            // Construct the connection string using account name and key.
            if (connectionString == null || connectionString.isBlank()) {
                connectionString = "DefaultEndpointsProtocol=https;AccountName=" + accountName +
                        ";AccountKey=" + accountKey + ";EndpointSuffix=core.windows.net";
            }
            BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                    .connectionString(connectionString)
                    .buildClient();
            this.containerClient = blobServiceClient.getBlobContainerClient(containerName);
            if (!this.containerClient.exists()) {
                // Optionally, create the container if it does not exist.
                this.containerClient.create();
            }
        } catch (Exception e) {
            throw new ExecutionException("Failed to create Azure Blob Storage connection using provided credentials", e);
        }
        // Perform parent initialization.
        super.init();
        // Initialize reader and writer so that their strategies may use the connection.
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
            // Test the connection by listing blobs in the container.
            containerClient.listBlobs();
        } catch (Exception e) {
            throw new RuntimeException("Azure connection test failed.", e);
        }
    }

    public File downloadFile(String fileName, Path path) throws ExecutionException {
        try {
            // Retrieve the BlobContainerClient from the server.
            if (this.getContainerClient() == null) {
                throw new ExecutionException("BlobContainerClient is not initialized.");
            }

            // Optionally, if you have a remote directory property, you could retrieve it here:
            Map<String, String> props = this.getResource().getResourceProperties();
            String remoteDirectory = props.get("azureRemoteDirectory"); // optional property
            String blobName = fileName;
            if (remoteDirectory != null && !remoteDirectory.isEmpty()) {
                // Ensure the directory ends with a slash
                if (!remoteDirectory.endsWith("/")) {
                    remoteDirectory += "/";
                }
                blobName = remoteDirectory + fileName;
            }

            // Get the BlobClient for the specified blob name.
            BlobClient blobClient = this.getContainerClient().getBlobClient(blobName);
            if (!blobClient.exists()) {
                throw new ExecutionException("No blob found with name: " + blobName);
            }

            // Download the blob's content into the temporary file.
            blobClient.downloadToFile(path.toString());

            // Return the downloaded file.
            return path.toFile();
        } catch (Exception e) {
            throw new ExecutionException("Failed to download file '" + fileName + "' from Azure Blob Storage", e);
        }
    }

    public Logger getLogger() {
        return logger;
    }

    /**
     * Provides the BlobContainerClient created during initialization.
     *
     * @return the BlobContainerClient instance.
     */
    public BlobContainerClient getContainerClient() {
        return containerClient;
    }
}
