package com.everyarrow.dittoe.io.database.servers.postgresql.reader;

import com.everyarrow.dittoe.io.database.servers.jdbc.reader.JDBCReader;
import com.everyarrow.dittoe.io.database.servers.postgresql.PostgreSQLServer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

public class PostgreSQLReader extends JDBCReader {

    public PostgreSQLReader() throws ExecutionException {
        super();
    }

    @Override
    public PostgreSQLServer getServer() {
        return (PostgreSQLServer) super.getServer();
    }

}
