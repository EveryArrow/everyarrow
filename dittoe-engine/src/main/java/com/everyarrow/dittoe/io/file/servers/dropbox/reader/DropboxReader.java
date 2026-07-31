package com.everyarrow.dittoe.io.file.servers.dropbox.reader;

import com.everyarrow.dittoe.io.file.FileReader;
import com.everyarrow.dittoe.io.file.servers.dropbox.DropboxServer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

/**
 * Concrete Dropbox reader.
 * <p>
 * Heavy initialization (including setting the CSV read strategy) is handled by the base class.
 */
public class DropboxReader extends FileReader {

    public DropboxReader() throws ExecutionException {
        super();
        // The base class handles setting the read strategy.
    }

    @Override
    public DropboxServer getServer() {
        return (DropboxServer) super.getServer();
    }
}
