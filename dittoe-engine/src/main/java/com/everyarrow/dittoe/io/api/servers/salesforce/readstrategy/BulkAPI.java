package com.everyarrow.dittoe.io.api.servers.salesforce.readstrategy;

import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.Field;

import java.util.List;

public class BulkAPI extends SalesforceReadStrategy {
    public BulkAPI() throws ExecutionException {
    }

    @Override
    public void read() throws ExecutionException {

    }

    @Override
    public void read(int limit) throws ExecutionException {

    }

    @Override
    public List<Long> getRange() throws ExecutionException {
        return List.of();
    }

    @Override
    public long getDataCount() throws ExecutionException {
        return 0;
    }

    @Override
    public int getAverageRowSize() throws ExecutionException {
        return 0;
    }

    @Override
    public List<Field> getFields() throws ExecutionException {
        return List.of();
    }
}
