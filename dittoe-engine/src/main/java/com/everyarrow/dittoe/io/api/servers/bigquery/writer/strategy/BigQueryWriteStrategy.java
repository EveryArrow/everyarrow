package com.everyarrow.dittoe.io.api.servers.bigquery.writer.strategy;

import com.google.cloud.bigquery.TableDataWriteChannel;
import com.everyarrow.dittoe.io.WriteStrategy;
import com.everyarrow.dittoe.io.api.servers.bigquery.writer.BigQueryWriter;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

public abstract class BigQueryWriteStrategy extends WriteStrategy {
    private TableDataWriteChannel tableDataWriteChannel;

    public BigQueryWriteStrategy() throws ExecutionException {
        super();
    }

    public abstract boolean dataExists() throws ExecutionException;

    public TableDataWriteChannel getTableDataWriteChannel() {
        return tableDataWriteChannel;
    }

    public void setTableDataWriteChannel(TableDataWriteChannel tableDataWriteChannel) {
        this.tableDataWriteChannel = tableDataWriteChannel;
    }

    @Override
    public BigQueryWriter getWriter() {
        return (BigQueryWriter) super.getWriter();
    }

}
