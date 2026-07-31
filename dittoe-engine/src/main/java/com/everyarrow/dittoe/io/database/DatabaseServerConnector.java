package com.everyarrow.dittoe.io.database;

import com.everyarrow.dittoe.io.ServerConnector;

import java.util.Optional;

public interface DatabaseServerConnector extends ServerConnector {
    default Optional<DatabaseReader> getDatabaseReaderOption() {
        return getReaderOption().map(DatabaseReader.class::cast);
    }

    default Optional<DatabaseWriter> getDatabaseWriterOption() {
        return getWriterOption().map(DatabaseWriter.class::cast);
    }

    default DatabaseReader requireDatabaseReader() {
        return getDatabaseReaderOption().orElseThrow(() ->
                new UnsupportedOperationException("This server instance does not expose a database reader"));
    }

    default DatabaseWriter requireDatabaseWriter() {
        return getDatabaseWriterOption().orElseThrow(() ->
                new UnsupportedOperationException("This server instance does not expose a database writer"));
    }
}
