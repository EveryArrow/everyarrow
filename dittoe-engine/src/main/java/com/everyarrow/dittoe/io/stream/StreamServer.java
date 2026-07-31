package com.everyarrow.dittoe.io.stream;

import com.everyarrow.dittoe.io.IOServer;
import com.everyarrow.dittoe.io.stream.reader.StreamReader;
import com.everyarrow.dittoe.io.stream.writer.StreamWriter;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

public abstract class StreamServer extends IOServer implements StreamServerConnector {

    public StreamServer(StreamReader streamReader, StreamWriter streamWriter) throws ExecutionException {
        super(streamReader, streamWriter);
    }
}
