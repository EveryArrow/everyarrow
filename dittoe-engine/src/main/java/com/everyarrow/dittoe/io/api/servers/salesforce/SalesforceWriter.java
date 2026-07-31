package com.everyarrow.dittoe.io.api.servers.salesforce;

import com.everyarrow.dittoe.io.api.APIWriter;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

public class SalesforceWriter extends APIWriter {

    public SalesforceWriter() throws ExecutionException {
        super();
    }

    @Override
    public SalesforceServer getServer() {
        return (SalesforceServer) super.getServer();
    }
}
