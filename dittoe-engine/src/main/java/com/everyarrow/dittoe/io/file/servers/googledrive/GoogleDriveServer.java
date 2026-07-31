package com.everyarrow.dittoe.io.file.servers.googledrive;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.everyarrow.dittoe.io.file.FileServer;
import com.everyarrow.dittoe.io.file.servers.googledrive.reader.GoogleDriveReader;
import com.everyarrow.dittoe.io.file.servers.googledrive.writer.GoogleDriveWriter;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * Concrete Google Drive server.
 * <p>
 * This server creates the Google Drive service using the service account credentials and
 * application name provided in the resource properties. It uses a GoogleDriveReader when used as a source
 * and a GoogleDriveWriter when used as a target.
 */
public class GoogleDriveServer extends FileServer {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private Drive driveService;
    private String fileId;

    /**
     * Minimal constructor. No heavy initialization is performed here.
     *
     * @param isSource true if this server is used as a source; false if used as a target.
     * @throws ExecutionException if instantiation fails.
     */
    public GoogleDriveServer(Boolean isSource) throws ExecutionException {
        // Pass minimal reader/writer objects to the parent constructor.
        super(isSource ? new GoogleDriveReader() : null, isSource ? null : new GoogleDriveWriter());
        if (isSource) {
            this.getReader().setServer(this);
        } else {
            this.getWriter().setServer(this);
        }
    }

    /**
     * Initializes the Google Drive server.
     * <p>
     * Reads the required credentials and configuration from the resource properties and creates the Drive service.
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
        String credentialsJson = props.get("googleDriveCredentialsJson");
        String applicationName = props.get("googleDriveApplicationName");
        this.fileId = props.get("googleDriveFileId");
        if (credentialsJson == null || applicationName == null || this.fileId == null) {
            throw new ExecutionException("Missing required Google Drive properties: googleDriveCredentialsJson, googleDriveApplicationName, and googleDriveFileId");
        }
        try {
            NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            ServiceAccountCredentials credentials = ServiceAccountCredentials.fromStream(
                    new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8)));
            HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credentials);

            driveService = new Drive.Builder(httpTransport, GsonFactory.getDefaultInstance(), requestInitializer)
                    .setApplicationName(applicationName)
                    .build();
        } catch (Exception e) {
            throw new ExecutionException("Failed to create Google Drive service", e);
        }
        // Perform parent initialization and initialize reader and writer.
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
            // Test the connection by attempting to retrieve file metadata.
            driveService.files().get(fileId).execute();
        } catch (Exception e) {
            throw new RuntimeException("Google Drive connection test failed.", e);
        }
    }

    public File downloadFile(String fileId, Path path) throws ExecutionException {
        try {
            // Use the Drive service to download the file's content as an InputStream.
            InputStream inputStream = driveService.files().get(fileId).executeMediaAsInputStream();

            // Copy the InputStream to the temporary file.
            Files.copy(inputStream, path, StandardCopyOption.REPLACE_EXISTING);
            inputStream.close();

            // Return the downloaded file.
            return path.toFile();
        } catch (Exception e) {
            throw new ExecutionException("Failed to download file with ID '" + fileId + "' from Google Drive", e);
        }
    }

    public Logger getLogger() {
        return logger;
    }

    /**
     * Returns the Google Drive service instance.
     *
     * @return the Drive instance.
     */
    public Drive getDriveService() {
        return driveService;
    }

    /**
     * Returns the file ID used by this server.
     *
     * @return the Google Drive file ID.
     */
    public String getFileId() {
        return fileId;
    }
}
