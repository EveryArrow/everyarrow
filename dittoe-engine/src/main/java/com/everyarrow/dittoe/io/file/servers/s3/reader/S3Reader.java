package com.everyarrow.dittoe.io.file.servers.s3.reader;

import com.everyarrow.dittoe.io.file.FileReader;
import com.everyarrow.dittoe.io.file.servers.s3.S3Server;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

/**
 * Concrete S3 reader that uses an CSVReadStrategy to retrieve data from S3.
 */
public class S3Reader extends FileReader {

    /**
     * Minimal constructor. Heavy initialization is deferred to the {@code init()} method.
     *
     * @throws ExecutionException if instantiation fails.
     */
    public S3Reader() throws ExecutionException {
        super();
    }

    @Override
    public S3Server getServer() {
        return (S3Server) super.getServer();
    }
}
