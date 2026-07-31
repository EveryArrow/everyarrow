package com.everyarrow.dittoe.io.api.servers.bigquery.reader;

import com.everyarrow.dittoe.io.api.APIReader;
import com.everyarrow.dittoe.io.api.servers.bigquery.BigQueryServer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

public class BigQueryReader extends APIReader {
    public BigQueryReader() throws ExecutionException {
        super();
    }


    @Override
    public void init() throws ExecutionException {
        super.init();
    }

    @Override
    public BigQueryServer getServer() {
        return (BigQueryServer) super.getServer();
    }
}
