package com.everyarrow.dittoe.io.file.servers.sftp.reader.strategy;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;
import com.everyarrow.dittoe.io.ReadStrategy;
import com.everyarrow.dittoe.io.file.servers.sftp.reader.SFTPReader;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.Field;
import com.everyarrow.dittoe.utility.CsvUtils;
import com.everyarrow.dittoe.utility.security.SafePathResolver;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Concrete CSV read strategy for SFTP.
 * <p>
 * Reads a CSV file from the remote SFTP server and populates the DataStore.
 */
public class CSVReadStrategy extends ReadStrategy {
    private static final String REMOTE_DIR_PROPERTY = "sftpRemoteDirectory";
    private static final String FILE_PROPERTY = "sftpFile";
    private static final String DELIMITER_OPTION = "csvDelimiter";
    private String delimiter = ","; // default delimiter

    public CSVReadStrategy() throws ExecutionException {
        super();
    }

    @Override
    public void init() throws ExecutionException {
        Map<String, String> props = this.getReader().getServer().getResource().getResourceProperties();
        this.delimiter = props.getOrDefault(DELIMITER_OPTION, ",");
        super.init();
    }

    private java.io.Reader getFileReader() throws ExecutionException {
        String basePath = this.getReader().getServer().getBasePath();
        String remoteFile;
        try {
            remoteFile = SafePathResolver.requireSafeRelativePath(
                    this.getReader().getServer().getResource().getResourceName(), "SFTP remote file");
        } catch (IllegalArgumentException e) {
            throw new ExecutionException("SFTP remote file path is not safe.", e);
        }

        if (basePath != null && !basePath.isBlank()) {
            remoteFile = joinRemotePath(basePath, remoteFile);
        }

        ChannelSftp sftp = this.getReader().getServer().getSftpChannel();
        try {
            return new BufferedReader(
                    new InputStreamReader(sftp.get(remoteFile), StandardCharsets.UTF_8));
        } catch (SftpException e) {
            throw new ExecutionException("Failed to open SFTP remote file.", e);
        }
    }


    @Override
    public void read() throws ExecutionException {
        CsvUtils.read(getFileReader(), this.getReader().getServer().getResource().getFields(), this.getReader().getServer().getResource().getResourceProperties());
    }

    public void read(int limit) throws ExecutionException {
        CsvUtils.read(getFileReader(), this.getReader().getServer().getResource().getFields(), this.getReader().getServer().getResource().getResourceProperties(), limit);
    }

    @Override
    public List<Long> getRange() throws ExecutionException {
        return CsvUtils.getRange(getFileReader(), this.getReader().getServer().getResource().getFields(), this.getReader().getServer().getResource().getResourceProperties());
    }

    @Override
    public long getDataCount() throws ExecutionException {
        return CsvUtils.getDataCount(getFileReader(), this.getReader().getServer().getResource().getFields(), this.getReader().getServer().getResource().getResourceProperties());
    }

    @Override
    public int getAverageRowSize() throws ExecutionException {
        return CsvUtils.getAverageRowSize(getFileReader(),
                this.getReader().getServer().getResource().getFields());
    }

    @Override
    public List<Field> getFields() throws ExecutionException {
        return CsvUtils.getFields(getFileReader());
    }

    @Override
    public SFTPReader getReader() {
        return (SFTPReader) super.getReader();
    }

    private String joinRemotePath(String basePath, String remoteFile) {
        String normalizedBase = basePath.replace('\\', '/');
        if (normalizedBase.endsWith("/")) {
            return normalizedBase + remoteFile;
        }
        return normalizedBase + "/" + remoteFile;
    }
}
