package com.everyarrow.dittoe.io.api;

import com.everyarrow.dittoe.io.IOServer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

public abstract class APIServer extends IOServer implements APIServerConnector {

    public APIServer(APIReader APIReader, APIWriter APIWriter) throws ExecutionException {
        super(APIReader, APIWriter);
    }

    @Override
    public void close() throws Exception {

    }
}
