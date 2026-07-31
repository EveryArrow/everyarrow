package com.everyarrow.dittoe.io.database;

import com.everyarrow.dittoe.io.IOServer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

public abstract class DatabaseServer extends IOServer implements DatabaseServerConnector {

    public DatabaseServer(DatabaseReader databaseReader, DatabaseWriter databaseWriter) throws ExecutionException {
        super(databaseReader, databaseWriter);
    }
}
