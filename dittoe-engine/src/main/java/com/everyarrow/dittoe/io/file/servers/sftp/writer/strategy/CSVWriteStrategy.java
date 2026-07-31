package com.everyarrow.dittoe.io.file.servers.sftp.writer.strategy;

import com.jcraft.jsch.ChannelSftp;
import com.everyarrow.dittoe.io.WriteStrategy;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.io.file.servers.sftp.writer.SFTPWriter;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.PipelineMetadata;
import com.everyarrow.dittoe.utility.CsvUtils;
import com.everyarrow.dittoe.utility.metadata.MetadataRepositoryAPI;
import com.everyarrow.dittoe.utility.security.SafePathResolver;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Concrete CSV write strategy for SFTP.
 * <p>
 * Data from the DataStore is written to a temporary CSV file and then uploaded to the remote SFTP server.
 */
public class CSVWriteStrategy extends WriteStrategy {
    private static final String REMOTE_DIR_PROPERTY = "sftpRemoteDirectory";
    private static final String FILE_PROPERTY = "sftpFile";
    private static final String DELIMITER_OPTION = "csvDelimiter";
    private String delimiter = ","; // default delimiter

    public CSVWriteStrategy() throws ExecutionException {
        super();
    }

    @Override
    public void init() throws ExecutionException {
        Map<String, String> props = this.getWriter().getServer().getResource().getResourceProperties();
        this.delimiter = props.getOrDefault(DELIMITER_OPTION, ",");
        super.init();
    }

    @Override
    public void write() throws ExecutionException {
        try {
            DataStoreInterface dataStore = DataStoreInterface.getDataStoreInterface();
            PipelineMetadata pipelineMetadata = MetadataRepositoryAPI.getPipelineMetadata();

            int size = dataStore.size();
            if (size == 0) return;

            String basePath = this.getWriter().getServer().getBasePath();
            String remoteFile = SafePathResolver.requireSafeRelativePath(
                    this.getWriter().getServer().getResource().getResourceName(), "SFTP remote file");

            Path tempFile = SafePathResolver.createTempFile("sftp", ".csv");
            try {
                CsvUtils.write(new FileWriter(tempFile.toFile(), true), dataStore, pipelineMetadata);

                if (basePath != null && !basePath.isBlank()) {
                    remoteFile = joinRemotePath(basePath, remoteFile);
                }
                // Upload the temporary file to the remote SFTP server.
                ChannelSftp sftp = this.getWriter().getServer().getSftpChannel();
                sftp.put(tempFile.toString(), remoteFile);
            } finally {
                Files.deleteIfExists(tempFile);
            }
            this.getWriter().notifyDataInsertionObservers(size);
        } catch (IOException e) {
            throw new ExecutionException("Failed to write CSV to temporary file for SFTP upload", e);
        } catch (Exception e) {
            throw new ExecutionException("Failed to upload CSV file to SFTP", e);
        }
    }

    @Override
    public SFTPWriter getWriter() {
        return (SFTPWriter) super.getWriter();
    }

    private String joinRemotePath(String basePath, String remoteFile) {
        String normalizedBase = basePath.replace('\\', '/');
        if (normalizedBase.endsWith("/")) {
            return normalizedBase + remoteFile;
        }
        return normalizedBase + "/" + remoteFile;
    }
}
