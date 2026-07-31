package com.everyarrow.dittoe.io.file.servers.ftp;

import org.apache.commons.net.ftp.FTPClient;
import com.everyarrow.dittoe.io.file.FileServer;
import com.everyarrow.dittoe.io.file.servers.ftp.reader.FTPReader;
import com.everyarrow.dittoe.io.file.servers.ftp.writer.FTPWriter;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Concrete FTP server implementation.
 * <p>
 * All heavy initialization (connection creation, login) is performed in the {@code init()} method.
 */
public class FTPServer extends FileServer {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private FTPClient ftpClient;

    /**
     * Minimal constructor. No heavy initialization is done here.
     *
     * @param isSource true if this server is used as a source; false if used as a target.
     * @throws ExecutionException if instantiation fails.
     */
    public FTPServer(Boolean isSource) throws ExecutionException {
        // Pass minimal reader/writer objects to the parent constructor.
        super(isSource ? new FTPReader() : null, isSource ? null : new FTPWriter());
        if (isSource) {
            this.getReader().setServer(this);
        } else {
            this.getWriter().setServer(this);
        }
    }

    /**
     * Initializes the FTP server.
     * <p>
     * Reads FTP credentials from the resource’s properties (ftpHost, ftpPort, ftpUser, ftpPassword)
     * and establishes an FTP connection.
     *
     * @throws ExecutionException if connection creation or login fails.
     */
    @Override
    public void init() throws ExecutionException {
        Map<String, String> props = this.getConnectionDetails();
        String host = props.get("ftpHost");
        String portStr = props.get("ftpPort");
        String user = props.get("ftpUser");
        String password = props.get("ftpPassword");
        boolean allowInsecurePlainFtp = Boolean.parseBoolean(props.getOrDefault("allowInsecurePlainFtp", "false"));

        if (host == null || user == null || password == null) {
            throw new ExecutionException("Missing required FTP credentials in connection details. " +
                    "Please set ftpHost, ftpUser, and ftpPassword.");
        }

        if (!allowInsecurePlainFtp) {
            throw new ExecutionException("Plain FTP is disabled by default because it sends credentials and data without TLS. "
                    + "Use SFTP/FTPS or set allowInsecurePlainFtp=true only for trusted legacy endpoints.");
        }

        int port = 21;
        if (portStr != null) {
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                // fallback to default port 21
            }
        }

        ftpClient = new FTPClient();
        try {
            logger.warn("Connecting to plaintext FTP because allowInsecurePlainFtp=true.");
            ftpClient.connect(host, port);
            if (!ftpClient.login(user, password)) {
                throw new ExecutionException("FTP login failed.");
            }
            ftpClient.enterLocalPassiveMode();
            // Optionally, set file type to binary:
            // ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE);
        } catch (IOException e) {
            throw new ExecutionException("Failed to initialize FTP connection", e);
        }

        // Initialize reader and writer.
        if (this.getReader() != null) {
            this.getReader().init();
        }
        if (this.getWriter() != null) {
            this.getWriter().init();
        }

        // Call parent's initialization.
        super.init();
    }

    @Override
    public void testConnection() {
        try {
            ftpClient.sendNoOp();
        } catch (IOException e) {
            throw new RuntimeException("FTP connection test failed.", e);
        }
    }

    public File downloadFile(String fileName, Path path) throws ExecutionException {
        try {
            // Retrieve resource properties.
            Map<String, String> props = this.getResource().getResourceProperties();

            // Optionally retrieve the remote directory from properties.
            String remoteDirectory = props.get("ftpRemoteDirectory");
            String remotePath;
            if (remoteDirectory != null && !remoteDirectory.isEmpty()) {
                // Ensure the remote directory ends with a "/"
                if (!remoteDirectory.endsWith("/")) {
                    remoteDirectory += "/";
                }
                remotePath = remoteDirectory + fileName;
            } else {
                remotePath = fileName;
            }

            // Open an output stream to the temporary file.
            try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
                // Retrieve (download) the remote file into the local temporary file.
                boolean success = ftpClient.retrieveFile(remotePath, fos);
                if (!success) {
                    throw new ExecutionException("Failed to download file '" + remotePath + "' from FTP server.");
                }
            }

            // Return the downloaded file.
            return path.toFile();
        } catch (Exception e) {
            throw new ExecutionException("Failed to download file '" + fileName + "' from FTP server", e);
        }
    }


    public FTPClient getFtpClient() {
        return ftpClient;
    }

    public Logger getLogger() {
        return logger;
    }
}
