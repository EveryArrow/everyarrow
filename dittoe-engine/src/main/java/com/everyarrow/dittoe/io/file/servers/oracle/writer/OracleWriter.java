package com.everyarrow.dittoe.io.file.servers.oracle.writer;

import com.everyarrow.dittoe.io.file.FileWriter;
import com.everyarrow.dittoe.io.file.servers.oracle.OracleServer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

/**
 * Concrete Oracle Cloud Storage writer.
 * <p>
 * Heavy initialization (including setting the CSV write strategy) is handled by the base class.
 */
public class OracleWriter extends FileWriter {

    public OracleWriter() throws ExecutionException {
        super();
        // Do not explicitly set the write strategy; the base class handles it.
    }

    @Override
    public OracleServer getServer() {
        return (OracleServer) super.getServer();
    }
}
