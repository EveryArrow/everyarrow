package com.everyarrow.dittoe.io.database.servers.snowflake;

import com.everyarrow.dittoe.io.database.servers.jdbc.writer.JDBCWriter;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

public class SnowflakeWriter extends JDBCWriter {

    public SnowflakeWriter() throws ExecutionException {
        super();
    }

    @Override
    public SnowflakeServer getServer() {
        return (SnowflakeServer) super.getServer();
    }
}
