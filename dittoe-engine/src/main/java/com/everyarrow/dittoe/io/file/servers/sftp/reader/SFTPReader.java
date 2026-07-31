package com.everyarrow.dittoe.io.file.servers.sftp.reader;

import com.everyarrow.dittoe.io.file.FileReader;
import com.everyarrow.dittoe.io.file.servers.sftp.SFTPServer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

/**
 * Concrete SFTP reader.
 * <p>
 * Heavy initialization (including setting the CSV read strategy) is handled by the base class.
 */
public class SFTPReader extends FileReader {

    public SFTPReader() throws ExecutionException {
        super();
        // Do not explicitly set the read strategy; the base class handles it.
    }

    @Override
    public SFTPServer getServer() {
        return (SFTPServer) super.getServer();
    }
}
