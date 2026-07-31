package com.everyarrow.dittoe.io.database.servers.mysql;

import com.everyarrow.dittoe.io.database.servers.jdbc.reader.JDBCReader;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

public class MySQLReader extends JDBCReader {

    public MySQLReader() throws ExecutionException {
        super();
    }

    @Override
    public MySQLServer getServer() {
        return (MySQLServer) super.getServer();
    }

}
