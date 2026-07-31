package com.everyarrow.dittoe.io.file.servers.localfile.reader;

import com.everyarrow.dittoe.io.file.FileReader;
import com.everyarrow.dittoe.io.file.servers.localfile.LocalFileServer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

public class LocalFileReader extends FileReader {
    public LocalFileReader() throws ExecutionException {
        super();
    }

    @Override
    public LocalFileServer getServer() {
        return (LocalFileServer) super.getServer();
    }
}
