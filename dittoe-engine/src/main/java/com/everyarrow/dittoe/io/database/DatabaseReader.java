package com.everyarrow.dittoe.io.database;

import com.everyarrow.dittoe.io.Reader;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;


public abstract class DatabaseReader extends Reader {

    public DatabaseReader() throws ExecutionException {
        super();
    }
}

