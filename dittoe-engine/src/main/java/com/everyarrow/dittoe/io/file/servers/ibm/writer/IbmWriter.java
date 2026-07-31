package com.everyarrow.dittoe.io.file.servers.ibm.writer;

import com.everyarrow.dittoe.io.file.FileWriter;
import com.everyarrow.dittoe.io.file.servers.ibm.IbmServer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

/**
 * Concrete IBM COS writer.
 * <p>
 * Heavy initialization (including setting the CSV write strategy) is handled by the base class.
 * </p>
 */
public class IbmWriter extends FileWriter {

    public IbmWriter() throws ExecutionException {
        super();
        // The base class handles setting the write strategy.
    }

    @Override
    public IbmServer getServer() {
        return (IbmServer) super.getServer();
    }
}
