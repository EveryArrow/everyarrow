package com.everyarrow.dittoe.io.database.servers.postgresql.writer;

import com.everyarrow.dittoe.io.database.servers.jdbc.writer.JDBCWriter;
import com.everyarrow.dittoe.io.database.servers.postgresql.PostgreSQLServer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

public class PostgreSQLWriter extends JDBCWriter {

    public PostgreSQLWriter() throws ExecutionException {
        super();
    }

    @Override
    public PostgreSQLServer getServer() {
        return (PostgreSQLServer) super.getServer();
    }
}
