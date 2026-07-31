package com.everyarrow.dittoe.io.file.servers.sftp.writer;

import com.everyarrow.dittoe.io.file.FileWriter;
import com.everyarrow.dittoe.io.file.servers.sftp.SFTPServer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

/**
 * Concrete SFTP writer.
 * <p>
 * Heavy initialization (including setting the CSV write strategy) is handled by the base class.
 */
public class SFTPWriter extends FileWriter {

    public SFTPWriter() throws ExecutionException {
        super();
        // Do not explicitly set the write strategy; the base class handles it.
    }

    @Override
    public SFTPServer getServer() {
        return (SFTPServer) super.getServer();
    }
}
