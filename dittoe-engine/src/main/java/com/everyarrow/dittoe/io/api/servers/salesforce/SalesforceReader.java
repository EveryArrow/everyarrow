package com.everyarrow.dittoe.io.api.servers.salesforce;

import com.everyarrow.dittoe.io.api.APIReader;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SalesforceReader extends APIReader {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public SalesforceReader() throws ExecutionException {
        super();
    }

    @Override
    public SalesforceServer getServer() {
        return (SalesforceServer) super.getServer();
    }

    public Logger getLogger() {
        return logger;
    }
}
