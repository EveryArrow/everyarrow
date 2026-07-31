package com.everyarrow.dittoe.io.file.servers.googledrive.reader;

import com.everyarrow.dittoe.io.file.FileReader;
import com.everyarrow.dittoe.io.file.servers.googledrive.GoogleDriveServer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

/**
 * Concrete Google Drive reader.
 * <p>
 * Heavy initialization (including setting the CSV read strategy) is handled by the base class.
 */
public class GoogleDriveReader extends FileReader {

    public GoogleDriveReader() throws ExecutionException {
        super();
        // The base class handles setting the read strategy.
    }

    @Override
    public GoogleDriveServer getServer() {
        return (GoogleDriveServer) super.getServer();
    }
}
