package com.everyarrow.dittoe.io.database.servers.mssql.writer;

import com.everyarrow.dittoe.io.database.servers.jdbc.writer.JDBCWriter;
import com.everyarrow.dittoe.io.database.servers.mssql.MSSQLServer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

public class MSSQLWriter extends JDBCWriter {

    public MSSQLWriter() throws ExecutionException {
        super();
    }

    @Override
    public MSSQLServer getServer() {
        return (MSSQLServer) super.getServer();
    }
}
