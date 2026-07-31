package com.everyarrow.dittoe.io.stream;

import com.everyarrow.dittoe.io.ServerConnector;
import com.everyarrow.dittoe.io.stream.reader.StreamReader;
import com.everyarrow.dittoe.io.stream.writer.StreamWriter;

import java.util.Optional;

public interface StreamServerConnector extends ServerConnector {
    default Optional<StreamReader> getStreamReaderOption() {
        return getReaderOption().map(StreamReader.class::cast);
    }

    default Optional<StreamWriter> getStreamWriterOption() {
        return getWriterOption().map(StreamWriter.class::cast);
    }

    default StreamReader requireStreamReader() {
        return getStreamReaderOption().orElseThrow(() ->
                new UnsupportedOperationException("This server instance does not expose a stream reader"));
    }

    default StreamWriter requireStreamWriter() {
        return getStreamWriterOption().orElseThrow(() ->
                new UnsupportedOperationException("This server instance does not expose a stream writer"));
    }
}
