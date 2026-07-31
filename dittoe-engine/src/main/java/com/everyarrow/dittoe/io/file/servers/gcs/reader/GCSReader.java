package com.everyarrow.dittoe.io.file.servers.gcs.reader;

import com.everyarrow.dittoe.io.file.FileReader;
import com.everyarrow.dittoe.io.file.servers.gcs.GCSServer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

/**
 * Concrete GCS reader.
 * <p>
 * All heavy initialization (including setting the CSV read strategy) is handled by the base class.
 */
public class GCSReader extends FileReader {

    public GCSReader() throws ExecutionException {
        super();
        // Do not set the read strategy explicitly; the base class handles it.
    }

    @Override
    public GCSServer getServer() {
        return (GCSServer) super.getServer();
    }
}
