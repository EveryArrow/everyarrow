package com.everyarrow.dittoe.io.database.servers.snowflake;

import com.everyarrow.dittoe.io.database.servers.jdbc.reader.JDBCReader;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

public class SnowflakeReader extends JDBCReader {

    public SnowflakeReader() throws ExecutionException {
        super();
    }

    @Override
    public SnowflakeServer getServer() {
        return (SnowflakeServer) super.getServer();
    }

}
