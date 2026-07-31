package com.everyarrow.dittoe.io.file.servers.localfile.writer.strategy;

import com.everyarrow.dittoe.io.WriteStrategy;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.io.file.servers.localfile.writer.LocalFileWriter;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import com.everyarrow.dittoe.utility.CsvUtils;
import com.everyarrow.dittoe.utility.metadata.MetadataRepositoryAPI;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CSVWriteStrategy extends WriteStrategy {

    public CSVWriteStrategy() throws ExecutionException {
        super();
    }

    @Override
    public void init() throws ExecutionException {
        super.init();
    }

    public Path getFilePath() {
        return this.getWriter().getServer()
                .resolveResourcePath(this.getWriter().getServer().getResource().getResourceName());
    }

    @Override
    public void write() throws ExecutionException {
        try {
            DataStoreInterface dataStore = DataStoreInterface.getDataStoreInterface();
            long rowCount = dataStore.size();
            
            Path filePath = this.getFilePath();
            
            // Create parent directories if they don't exist
            Path parentDir = filePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            
            CsvUtils.write(new FileWriter(filePath.toFile(), true),
                    dataStore,
                    MetadataRepositoryAPI.getPipelineMetadata());
            
            // Notify observers of successful write
            this.getWriter().notifyDataInsertionObservers(rowCount);
        } catch (Exception e) {
            throw new ExecutionException("Could not write data", e);
        }
    }


    @Override
    public LocalFileWriter getWriter() {
        return (LocalFileWriter) super.getWriter();
    }
}
