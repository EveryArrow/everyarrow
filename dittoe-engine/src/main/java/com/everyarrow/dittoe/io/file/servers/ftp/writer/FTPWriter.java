package com.everyarrow.dittoe.io.file.servers.ftp.writer;

import com.everyarrow.dittoe.io.file.FileWriter;
import com.everyarrow.dittoe.io.file.servers.ftp.FTPServer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

/**
 * Concrete FTP writer.
 * <p>
 * Heavy initialization (including strategy setup) is handled by the base class.
 */
public class FTPWriter extends FileWriter {

    public FTPWriter() throws ExecutionException {
        super();
        // No need to explicitly set the write strategy—the base class handles it.
    }

    @Override
    public FTPServer getServer() {
        return (FTPServer) super.getServer();
    }
}
