package com.everyarrow.dittoe.io.database.servers.sqlite.reader;

import com.everyarrow.dittoe.io.database.servers.jdbc.reader.JDBCReader;
import com.everyarrow.dittoe.io.database.servers.sqlite.SQLiteServer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

public class SQLiteReader extends JDBCReader {

    public SQLiteReader() throws ExecutionException {
        super();
    }

    @Override
    public SQLiteServer getServer() {
        return (SQLiteServer) super.getServer();
    }

}
