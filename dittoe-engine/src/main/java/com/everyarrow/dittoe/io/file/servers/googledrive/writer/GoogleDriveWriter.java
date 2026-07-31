package com.everyarrow.dittoe.io.file.servers.googledrive.writer;

import com.everyarrow.dittoe.io.file.FileWriter;
import com.everyarrow.dittoe.io.file.servers.googledrive.GoogleDriveServer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

/**
 * Concrete Google Drive writer.
 * <p>
 * Heavy initialization (including setting the CSV write strategy) is handled by the base class.
 */
public class GoogleDriveWriter extends FileWriter {

    public GoogleDriveWriter() throws ExecutionException {
        super();
        // The base class handles setting the write strategy.
    }

    @Override
    public GoogleDriveServer getServer() {
        return (GoogleDriveServer) super.getServer();
    }
}
