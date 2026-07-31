package com.everyarrow.dittoe.io.file.servers.azure.reader;

import com.everyarrow.dittoe.io.file.FileReader;
import com.everyarrow.dittoe.io.file.servers.azure.AzureServer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

/**
 * Concrete Azure reader.
 * <p>
 * Heavy initialization (including setting the CSV read strategy) is handled by the base class.
 */
public class AzureReader extends FileReader {

    public AzureReader() throws ExecutionException {
        super();
        // No need to set the read strategy explicitly; the base class already handles it.
    }

    @Override
    public AzureServer getServer() {
        return (AzureServer) super.getServer();
    }
}
