package com.everyarrow.dittoe.io.api.servers.salesforce;

import com.everyarrow.dittoe.io.api.APIServer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

public class SalesforceServer extends APIServer {

    public SalesforceServer(Boolean isSource) throws ExecutionException {
        super(isSource ? new SalesforceReader() : null, isSource ? null : new SalesforceWriter());

        if (isSource)
            this.getReader().setServer(this);
        else
            this.getWriter().setServer(this);
    }

    @Override
    public void init() throws ExecutionException {
        super.init();
    }

    @Override
    public void testConnection() {
        throw new UnsupportedOperationException("Server SalesforceServer does not support connection testing yet.");
    }
}
