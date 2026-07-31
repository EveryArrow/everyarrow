package com.everyarrow.dittoe.io.file.servers.s3.writer;

import com.everyarrow.dittoe.io.file.FileWriter;
import com.everyarrow.dittoe.io.file.servers.s3.S3Server;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

/**
 * Concrete S3 writer that uses an CSVWriteStrategy to write data to S3.
 */
public class S3Writer extends FileWriter {

    /**
     * Minimal constructor. Heavy initialization is deferred to the {@code init()} method.
     *
     * @throws ExecutionException if instantiation fails.
     */
    public S3Writer() throws ExecutionException {
        super();
    }

    @Override
    public S3Server getServer() {
        return (S3Server) super.getServer();
    }
}
