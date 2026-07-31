package com.everyarrow.dittoe.io.file.servers.dropbox;

import com.dropbox.core.DbxDownloader;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.everyarrow.dittoe.io.file.FileServer;
import com.everyarrow.dittoe.io.file.servers.dropbox.reader.DropboxReader;
import com.everyarrow.dittoe.io.file.servers.dropbox.writer.DropboxWriter;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * Concrete Dropbox server that manages Dropbox connection and server‐related functionality.
 * <p>
 * All heavy initialization (such as creating the Dropbox client using the access token)
 * is performed in the {@code init()} method. This server uses a DropboxReader when used as a source
 * and a DropboxWriter when used as a target.
 */
public class DropboxServer extends FileServer {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private DbxClientV2 client;

    /**
     * Minimal constructor. No heavy initialization is done here.
     *
     * @param isSource true if this server is used as a source; false if used as a target.
     * @throws ExecutionException if instantiation fails.
     */
    public DropboxServer(Boolean isSource) throws ExecutionException {
        // Pass minimal reader/writer objects to the parent constructor.
        super(isSource ? new DropboxReader() : null, isSource ? null : new DropboxWriter());
        if (isSource) {
            this.getReader().setServer(this);
        } else {
            this.getWriter().setServer(this);
        }
    }

    /**
     * Initializes the Dropbox server.
     * <p>
     * This method reads the required credentials from the resource properties and creates the Dropbox client.
     * The following properties must be defined:
     * <ul>
     *   <li>dropboxAccessToken</li>
     * </ul>
     * After creating the client, it calls {@code init()} on the parent as well as on the associated reader and writer.
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
        String accessToken = props.get("dropboxAccessToken");
        if (accessToken == null) {
            throw new ExecutionException("Missing dropboxAccessToken in resource properties.");
        }
        try {
            DbxRequestConfig config = DbxRequestConfig.newBuilder("DITTOE-Dropbox/1.0").build();
            this.client = new DbxClientV2(config, accessToken);
        } catch (Exception e) {
            throw new ExecutionException("Failed to create Dropbox client", e);
        }
        // Perform parent initialization.
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
            // Test connection by fetching the current account info.
            client.users().getCurrentAccount();
        } catch (Exception e) {
            throw new RuntimeException("Dropbox connection test failed.", e);
        }
    }

    public File downloadFile(String filePath, Path path) throws ExecutionException {
        try {
            // Download the file from Dropbox.
            // 'filePath' is the full path of the file in Dropbox (e.g., "/folder/file.csv").
            DbxDownloader<FileMetadata> downloader = client.files().download(filePath);

            // Copy the downloaded content into the temporary file.
            try (InputStream in = downloader.getInputStream()) {
                Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
            }

            // Return the downloaded file.
            return path.toFile();
        } catch (Exception e) {
            throw new ExecutionException("Failed to download file '" + filePath + "' from Dropbox", e);
        }
    }

    public Logger getLogger() {
        return logger;
    }

    /**
     * Returns the Dropbox client.
     *
     * @return the DbxClientV2 instance.
     */
    public DbxClientV2 getClient() {
        return client;
    }
}
