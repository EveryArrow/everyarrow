package com.everyarrow.dittoe.io.database.servers.mssql.reader;

import com.everyarrow.dittoe.io.database.servers.jdbc.reader.JDBCReader;
import com.everyarrow.dittoe.io.database.servers.mssql.MSSQLServer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

public class MSSQLReader extends JDBCReader {

    public MSSQLReader() throws ExecutionException {
        super();
    }

    @Override
    public MSSQLServer getServer() {
        return (MSSQLServer) super.getServer();
    }

}
