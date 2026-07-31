package com.everyarrow.dittoe.io.file.servers.sftp;

import com.jcraft.jsch.*;
import com.everyarrow.dittoe.io.file.FileServer;
import com.everyarrow.dittoe.io.file.servers.sftp.reader.SFTPReader;
import com.everyarrow.dittoe.io.file.servers.sftp.writer.SFTPWriter;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Concrete SFTP server that handles SFTP connection creation and other server-related functionalities.
 * <p>
 * All heavy initialization (including creating the SFTP connection using credentials) is performed
 * in the {@code init()} method. This server instantiates a {@code SFTPReader} when used as a source
 * and a {@code SFTPWriter} when used as a target.
 */
public class SFTPServer extends FileServer {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private Session session;
    private ChannelSftp sftpChannel;

    /**
     * Minimal constructor. No heavy initialization is done here.
     * The reader and writer objects are created but are not fully initialized.
     *
     * @param isSource true if the server is used as a source; false if used as a target.
     * @throws ExecutionException if instantiation fails.
     */
    public SFTPServer(Boolean isSource) throws ExecutionException {
        // Pass minimal reader/writer objects to the parent constructor.
        super(isSource ? new SFTPReader() : null, isSource ? null : new SFTPWriter());
        if (isSource) {
            this.getReader().setServer(this);
        } else {
            this.getWriter().setServer(this);
        }
    }

    /**
     * Initializes the SFTP server.
     * <p>
     * This method reads the following properties from the server’s resource:
     * <ul>
     *   <li>sftpHost – the SFTP server hostname</li>
     *   <li>sftpPort – the SFTP server port</li>
     *   <li>sftpUser – the username</li>
     *   <li>sftpPassword – the password</li>
     * </ul>
     * After connecting, it calls {@code init()} on the parent and the associated reader and writer.
     *
     * @throws ExecutionException if connection creation or initialization fails.
     */
    @Override
    public void init() throws ExecutionException {
        Map<String, String> props = this.getConnectionDetails();
        String host = props.get("sftpHost");
        String portStr = props.get("sftpPort");
        String user = props.get("sftpUser");
        String password = props.get("sftpPassword");
        if (host == null || portStr == null || user == null || password == null) {
            throw new ExecutionException("Missing required SFTP credentials in resource properties. " +
                    "Please set sftpHost, sftpPort, sftpUser, and sftpPassword.");
        }
        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            throw new ExecutionException("Invalid SFTP port number: " + portStr, e);
        }
        try {
            JSch jsch = new JSch();
            configureHostKeyChecking(jsch, props, host);
            session = jsch.getSession(user, host, port);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking",
                    Boolean.parseBoolean(props.getOrDefault("allowInsecureSftpHostKey", "false")) ? "no" : "yes");
            session.connect();
            Channel channel = session.openChannel("sftp");
            channel.connect();
            sftpChannel = (ChannelSftp) channel;
        } catch (JSchException e) {
            throw new ExecutionException("Failed to create SFTP connection", e);
        }

        // Initialize reader and writer so that their strategies can use the SFTP channel.
        if (this.getReader() != null) {
            this.getReader().init();
        }
        if (this.getWriter() != null) {
            this.getWriter().init();
        }

        // Perform parent initialization.
        super.init();
    }

    @Override
    public void testConnection() {
        try {
            // Attempt to list the current directory.
            sftpChannel.ls(".");
        } catch (SftpException e) {
            throw new RuntimeException("SFTP connection test failed.", e);
        }
    }

    public Logger getLogger() {
        return logger;
    }

    /**
     * Returns the SFTP channel for performing SFTP operations.
     *
     * @return the ChannelSftp instance.
     */
    public ChannelSftp getSftpChannel() {
        return sftpChannel;
    }

    /**
     * Returns the SFTP session.
     *
     * @return the Session instance.
     */
    public Session getSession() {
        return session;
    }

    /**
     * Disconnects and cleans up the SFTP connection.
     *
     * @throws Exception if an error occurs during disconnection.
     */
    @Override
    public void close() throws Exception {
        if (sftpChannel != null && sftpChannel.isConnected()) {
            sftpChannel.disconnect();
        }
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        super.close();
    }


    public File downloadFile(String fileName, Path path) throws ExecutionException {
        try {
            // Retrieve resource properties.
            Map<String, String> props = this.getResource().getResourceProperties();

            // Check for an optional remote directory.
            String remoteDirectory = props.get("ftpRemoteDirectory");
            String remotePath;
            if (remoteDirectory != null && !remoteDirectory.isEmpty()) {
                // Ensure the remote directory ends with a slash.
                if (!remoteDirectory.endsWith("/")) {
                    remoteDirectory += "/";
                }
                remotePath = remoteDirectory + fileName;
            } else {
                remotePath = fileName;
            }

            // Use the SFTP channel (via getSftpChannel()) to download the file.
            try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
                try (InputStream inputStream = getSftpChannel().get(remotePath)) {
                    inputStream.transferTo(fos);
                }
            }

            // Return the downloaded file.
            return path.toFile();
        } catch (Exception e) {
            throw new ExecutionException("Failed to download file '" + fileName + "' from the FTP server.", e);
        }
    }

    private void configureHostKeyChecking(JSch jsch, Map<String, String> props, String host) throws JSchException, ExecutionException {
        String knownHostsPath = props.get("knownHostsPath");
        String expectedFingerprint = props.get("expectedHostKeyFingerprint");
        boolean allowInsecure = Boolean.parseBoolean(props.getOrDefault("allowInsecureSftpHostKey", "false"));

        if (knownHostsPath != null && !knownHostsPath.isBlank()) {
            jsch.setKnownHosts(knownHostsPath);
            return;
        }

        if (expectedFingerprint != null && !expectedFingerprint.isBlank()) {
            jsch.setHostKeyRepository(new ExpectedFingerprintHostKeyRepository(jsch, host, expectedFingerprint));
            return;
        }

        if (allowInsecure) {
            logger.warn("SFTP host key checking is disabled by explicit insecure opt-in.");
            return;
        }

        throw new ExecutionException("SFTP requires knownHostsPath or expectedHostKeyFingerprint. "
                + "Set allowInsecureSftpHostKey=true only for trusted local development.");
    }

    private static final class ExpectedFingerprintHostKeyRepository implements HostKeyRepository {
        private final JSch jsch;
        private final String expectedHost;
        private final String expectedFingerprint;

        private ExpectedFingerprintHostKeyRepository(JSch jsch, String expectedHost, String expectedFingerprint) {
            this.jsch = jsch;
            this.expectedHost = expectedHost;
            this.expectedFingerprint = normalize(expectedFingerprint);
        }

        @Override
        public int check(String host, byte[] key) {
            try {
                HostKey hostKey = new HostKey(host, key);
                boolean hostMatches = host.equals(expectedHost) || host.endsWith(":" + expectedHost);
                boolean fingerprintMatches = normalize(hostKey.getFingerPrint(jsch)).equals(expectedFingerprint);
                return hostMatches && fingerprintMatches ? OK : CHANGED;
            } catch (JSchException e) {
                return CHANGED;
            }
        }

        @Override
        public void add(HostKey hostkey, UserInfo ui) {
        }

        @Override
        public void remove(String host, String type) {
        }

        @Override
        public void remove(String host, String type, byte[] key) {
        }

        @Override
        public String getKnownHostsRepositoryID() {
            return "expected-fingerprint";
        }

        @Override
        public HostKey[] getHostKey() {
            return new HostKey[0];
        }

        @Override
        public HostKey[] getHostKey(String host, String type) {
            return new HostKey[0];
        }

        private static String normalize(String fingerprint) {
            return fingerprint.trim().toLowerCase(Locale.ROOT);
        }
    }

}
