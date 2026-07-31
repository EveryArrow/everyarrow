package com.everyarrow.dittoe.io.file.servers.ftp.writer.strategy;

import org.apache.commons.net.ftp.FTPClient;
import com.everyarrow.dittoe.io.WriteStrategy;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.io.file.servers.ftp.writer.FTPWriter;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.PipelineMetadata;
import com.everyarrow.dittoe.utility.CsvUtils;
import com.everyarrow.dittoe.utility.metadata.MetadataRepositoryAPI;
import com.everyarrow.dittoe.utility.security.SafePathResolver;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Concrete FTP CSV write strategy.
 * <p>
 * Writes CSV data from the DataStore to a temporary file and uploads it via FTP.
 */
public class CSVWriteStrategy extends WriteStrategy {
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
            if (size == 0) {
                return;
            }
            String remoteFile = SafePathResolver.requireSafeRelativePath(
                    this.getWriter().getServer().getResource().getResourceName(), "FTP remote file");
            if (remoteFile == null) {
                throw new ExecutionException("ftpFile property not provided in resource properties.");
            }

            Path tempFile = SafePathResolver.createTempFile("ftp", ".csv");
            try {
                CsvUtils.write(new FileWriter(tempFile.toFile(), true), dataStore, pipelineMetadata);

                // Upload the temporary file using FTP.
                FTPClient ftpClient = this.getWriter().getServer().getFtpClient();
                try (FileInputStream fis = new FileInputStream(tempFile.toFile())) {
                    if (!ftpClient.storeFile(remoteFile, fis)) {
                        throw new ExecutionException("FTP storeFile failed.");
                    }
                }
            } finally {
                Files.deleteIfExists(tempFile);
            }
            this.getWriter().notifyDataInsertionObservers(size);
        } catch (IllegalArgumentException | IOException e) {
            throw new ExecutionException("Failed to write data to FTP server", e);
        }
    }

    @Override
    public FTPWriter getWriter() {
        return (FTPWriter) super.getWriter();
    }
}
