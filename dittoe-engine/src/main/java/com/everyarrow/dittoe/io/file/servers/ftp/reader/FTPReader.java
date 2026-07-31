package com.everyarrow.dittoe.io.file.servers.ftp.reader;

import com.everyarrow.dittoe.io.file.FileReader;
import com.everyarrow.dittoe.io.file.servers.ftp.FTPServer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

/**
 * Concrete FTP reader.
 * <p>
 * Heavy initialization (including strategy setup) is handled by the base class.
 */
public class FTPReader extends FileReader {

    public FTPReader() throws ExecutionException {
        super();
        // No need to explicitly set the read strategy—the base class handles it.
    }

    @Override
    public FTPServer getServer() {
        return (FTPServer) super.getServer();
    }
}
