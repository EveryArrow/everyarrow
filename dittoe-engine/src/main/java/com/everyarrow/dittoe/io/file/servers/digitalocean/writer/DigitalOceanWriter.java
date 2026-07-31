package com.everyarrow.dittoe.io.file.servers.digitalocean.writer;

import com.everyarrow.dittoe.io.file.FileWriter;
import com.everyarrow.dittoe.io.file.servers.digitalocean.DigitalOceanServer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

/**
 * Concrete DigitalOcean Spaces writer.
 * <p>
 * Heavy initialization (including setting the CSV write strategy) is handled by the base class.
 * </p>
 */
public class DigitalOceanWriter extends FileWriter {

    public DigitalOceanWriter() throws ExecutionException {
        super();
        // Do not explicitly set the write strategy; the base class handles it.
    }

    @Override
    public DigitalOceanServer getServer() {
        return (DigitalOceanServer) super.getServer();
    }
}
