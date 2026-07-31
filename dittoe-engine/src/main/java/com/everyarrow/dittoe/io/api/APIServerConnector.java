package com.everyarrow.dittoe.io.api;

import com.everyarrow.dittoe.io.ServerConnector;

import java.util.Optional;

public interface APIServerConnector extends ServerConnector {
    default Optional<APIReader> getAPIReaderOption() {
        return getReaderOption().map(APIReader.class::cast);
    }

    default Optional<APIWriter> getAPIWriterOption() {
        return getWriterOption().map(APIWriter.class::cast);
    }

    default APIReader requireAPIReader() {
        return getAPIReaderOption().orElseThrow(() ->
                new UnsupportedOperationException("This server instance does not expose an API reader"));
    }

    default APIWriter requireAPIWriter() {
        return getAPIWriterOption().orElseThrow(() ->
                new UnsupportedOperationException("This server instance does not expose an API writer"));
    }
}
