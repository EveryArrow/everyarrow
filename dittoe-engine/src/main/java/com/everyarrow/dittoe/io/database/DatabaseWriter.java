package com.everyarrow.dittoe.io.database;

import com.everyarrow.dittoe.io.Writer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

public abstract class DatabaseWriter extends Writer {

    public DatabaseWriter() throws ExecutionException {
        super();
    }
}

