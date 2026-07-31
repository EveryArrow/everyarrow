package com.everyarrow.dittoe.io.file.servers.ibm.reader;

import com.everyarrow.dittoe.io.file.FileReader;
import com.everyarrow.dittoe.io.file.servers.ibm.IbmServer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

/**
 * Concrete IBM COS reader.
 * <p>
 * Heavy initialization (including setting the CSV read strategy) is handled by the base class.
 * </p>
 */
public class IbmReader extends FileReader {

    public IbmReader() throws ExecutionException {
        super();
        // The base class handles setting the read strategy.
    }

    @Override
    public IbmServer getServer() {
        return (IbmServer) super.getServer();
    }
}
