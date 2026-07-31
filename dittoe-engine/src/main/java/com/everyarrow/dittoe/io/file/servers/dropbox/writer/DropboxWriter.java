package com.everyarrow.dittoe.io.file.servers.dropbox.writer;

import com.everyarrow.dittoe.io.file.FileWriter;
import com.everyarrow.dittoe.io.file.servers.dropbox.DropboxServer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

/**
 * Concrete Dropbox writer.
 * <p>
 * Heavy initialization (including setting the CSV write strategy) is handled by the base class.
 */
public class DropboxWriter extends FileWriter {

    public DropboxWriter() throws ExecutionException {
        super();
        // The base class handles setting the write strategy.
    }

    @Override
    public DropboxServer getServer() {
        return (DropboxServer) super.getServer();
    }
}
