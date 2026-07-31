package com.everyarrow.dittoe.io.file.servers.azure.writer;

import com.everyarrow.dittoe.io.file.FileWriter;
import com.everyarrow.dittoe.io.file.servers.azure.AzureServer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

/**
 * Concrete Azure writer.
 * <p>
 * Heavy initialization (including setting the CSV write strategy) is handled by the base class.
 */
public class AzureWriter extends FileWriter {

    public AzureWriter() throws ExecutionException {
        super();
        // No need to set the write strategy explicitly; the base class already handles it.
    }

    @Override
    public AzureServer getServer() {
        return (AzureServer) super.getServer();
    }
}
