package com.everyarrow.dittoe.io.file;

import com.everyarrow.dittoe.io.ServerConnector;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

public interface FileServerConnector extends ServerConnector {
    default Optional<FileReader> getFileReaderOption() {
        return getReaderOption().map(FileReader.class::cast);
    }

    default Optional<FileWriter> getFileWriterOption() {
        return getWriterOption().map(FileWriter.class::cast);
    }

    default FileReader requireFileReader() {
        return getFileReaderOption().orElseThrow(() ->
                new UnsupportedOperationException("This server instance does not expose a file reader"));
    }

    default FileWriter requireFileWriter() {
        return getFileWriterOption().orElseThrow(() ->
                new UnsupportedOperationException("This server instance does not expose a file writer"));
    }

    String getBasePath();

    File downloadFile(String fileName, Path path) throws ExecutionException;
}
