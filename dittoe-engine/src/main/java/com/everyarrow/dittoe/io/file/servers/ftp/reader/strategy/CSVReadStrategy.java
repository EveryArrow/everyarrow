package com.everyarrow.dittoe.io.file.servers.ftp.reader.strategy;

import org.apache.commons.net.ftp.FTPClient;
import com.everyarrow.dittoe.io.ReadStrategy;
import com.everyarrow.dittoe.io.file.servers.ftp.reader.FTPReader;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.Field;
import com.everyarrow.dittoe.utility.CsvUtils;
import com.everyarrow.dittoe.utility.security.SafePathResolver;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Concrete FTP CSV read strategy.
 * <p>
 * Reads a CSV file from an FTP server and populates the DataStore.
 */
public class CSVReadStrategy extends ReadStrategy {
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
        FTPClient ftpClient = this.getReader().getServer().getFtpClient();
        String remoteFile;
        try {
            remoteFile = SafePathResolver.requireSafeRelativePath(
                    this.getReader().getServer().getResource().getResourceName(), "FTP remote file");
        } catch (IllegalArgumentException e) {
            throw new ExecutionException("FTP remote file path is not safe.", e);
        }

        Path localFile;
        try {
            localFile = SafePathResolver.createTempFile("ftp-read", ".csv");
        } catch (IOException e) {
            throw new ExecutionException("Failed to create controlled FTP staging file", e);
        }

        try (OutputStream fos = Files.newOutputStream(localFile)) {
            if (!ftpClient.retrieveFile(remoteFile, fos)) {
                throw new ExecutionException("Failed to retrieve FTP file: " + remoteFile);
            }
        } catch (IOException e) {
            throw new ExecutionException("Failed to retrieve FTP file: " + remoteFile, e);
        }

        try {
            InputStream inputStream = new DeleteOnCloseInputStream(Files.newInputStream(localFile), localFile);
            return new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ExecutionException("Failed to open FTP staging file", e);
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
    public FTPReader getReader() {
        return (FTPReader) super.getReader();
    }

    private static final class DeleteOnCloseInputStream extends FilterInputStream {
        private final Path file;

        private DeleteOnCloseInputStream(InputStream inputStream, Path file) {
            super(inputStream);
            this.file = file;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                Files.deleteIfExists(file);
            }
        }
    }
}
