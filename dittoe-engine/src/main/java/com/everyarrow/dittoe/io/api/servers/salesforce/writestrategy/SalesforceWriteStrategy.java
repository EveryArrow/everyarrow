package com.everyarrow.dittoe.io.api.servers.salesforce.writestrategy;

import com.everyarrow.dittoe.io.WriteStrategy;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

public abstract class SalesforceWriteStrategy extends WriteStrategy {

    public SalesforceWriteStrategy() throws ExecutionException {
        super();
    }
}
