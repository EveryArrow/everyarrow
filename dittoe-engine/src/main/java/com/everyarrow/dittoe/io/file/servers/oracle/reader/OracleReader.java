package com.everyarrow.dittoe.io.file.servers.oracle.reader;

import com.everyarrow.dittoe.io.file.FileReader;
import com.everyarrow.dittoe.io.file.servers.oracle.OracleServer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

/**
 * Concrete Oracle Cloud Storage reader.
 * <p>
 * Heavy initialization (including setting the CSV read strategy) is handled by the base class.
 */
public class OracleReader extends FileReader {

    public OracleReader() throws ExecutionException {
        super();
        // Do not explicitly set the read strategy; the base class handles it.
    }

    @Override
    public OracleServer getServer() {
        return (OracleServer) super.getServer();
    }
}
