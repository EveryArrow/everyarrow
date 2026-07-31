package com.everyarrow.dittoe.io.database.servers.sqlite.writer;

import com.everyarrow.dittoe.io.database.servers.jdbc.writer.JDBCWriter;
import com.everyarrow.dittoe.io.database.servers.sqlite.SQLiteServer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

public class SQLiteWriter extends JDBCWriter {

    public SQLiteWriter() throws ExecutionException {
        super();
    }

    @Override
    public SQLiteServer getServer() {
        return (SQLiteServer) super.getServer();
    }
}
