package com.everyarrow.dittoe.io.api.servers.salesforce.readstrategy;

import com.everyarrow.dittoe.io.ReadStrategy;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

public abstract class SalesforceReadStrategy extends ReadStrategy {
    protected SalesforceReadStrategy() throws ExecutionException {
        super();
    }
}
