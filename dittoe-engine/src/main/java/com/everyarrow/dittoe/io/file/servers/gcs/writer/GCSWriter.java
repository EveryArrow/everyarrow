package com.everyarrow.dittoe.io.file.servers.gcs.writer;

import com.everyarrow.dittoe.io.file.FileWriter;
import com.everyarrow.dittoe.io.file.servers.gcs.GCSServer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

/**
 * Concrete GCS writer.
 * <p>
 * All heavy initialization (including setting the CSV write strategy) is handled by the base class.
 */
public class GCSWriter extends FileWriter {

    public GCSWriter() throws ExecutionException {
        super();
        // Do not set the write strategy explicitly; the base class handles it.
    }

    @Override
    public GCSServer getServer() {
        return (GCSServer) super.getServer();
    }
}
