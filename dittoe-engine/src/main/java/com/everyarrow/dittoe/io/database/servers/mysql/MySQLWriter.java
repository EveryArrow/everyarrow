package com.everyarrow.dittoe.io.database.servers.mysql;

import com.everyarrow.dittoe.io.database.servers.jdbc.writer.JDBCWriter;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

public class MySQLWriter extends JDBCWriter {

    public MySQLWriter() throws ExecutionException {
        super();
    }

    @Override
    public MySQLServer getServer() {
        return (MySQLServer) super.getServer();
    }
}
