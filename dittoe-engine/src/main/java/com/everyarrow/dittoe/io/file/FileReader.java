package com.everyarrow.dittoe.io.file;

import com.everyarrow.dittoe.io.Reader;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;


public abstract class FileReader extends Reader {

    public FileReader() throws ExecutionException {
        super();
    }


    @Override
    public FileServer getServer() {
        return (FileServer) super.getServer();
    }
}

