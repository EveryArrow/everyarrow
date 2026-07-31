package com.everyarrow.dittoe.io.file.servers.digitalocean.reader;

import com.everyarrow.dittoe.io.file.FileReader;
import com.everyarrow.dittoe.io.file.servers.digitalocean.DigitalOceanServer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

/**
 * Concrete DigitalOcean Spaces reader.
 * <p>
 * Heavy initialization (including setting the CSV read strategy) is handled by the base class.
 * </p>
 */
public class DigitalOceanReader extends FileReader {

    public DigitalOceanReader() throws ExecutionException {
        super();
        // Do not explicitly set the read strategy; the base class handles it.
    }

    @Override
    public DigitalOceanServer getServer() {
        return (DigitalOceanServer) super.getServer();
    }
}
