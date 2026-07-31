package com.everyarrow.dittoe.io.file.servers.localfile.writer;

import com.everyarrow.dittoe.io.file.FileWriter;
import com.everyarrow.dittoe.io.file.servers.localfile.LocalFileServer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import com.everyarrow.dittoe.utility.security.SafePathResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LocalFileWriter extends FileWriter {

    private static final String OUTPUT_DIRECTORY = "outputDirectory";

    /**
     * Constructor.
     *
     * @throws ExecutionException Thrown if initialization fails.
     */
    public LocalFileWriter() throws ExecutionException {
        super();
    }

    @Override
    public LocalFileServer getServer() {
        return (LocalFileServer) super.getServer();
    }

    public String getOutputFilePath() throws IOException {
        String directory = this.getServer().getResource().getResourceProperties().getOrDefault(OUTPUT_DIRECTORY, "");

        String outputFileName = this.getServer().getResource().getResourceName()
                .concat(this.getServer().getSuffix());
        Path outputFile = SafePathResolver.resolveUnderBase(
                directory.isBlank() ? this.getServer().getBasePath() : directory,
                outputFileName);
        if (outputFile.getParent() != null) {
            Files.createDirectories(outputFile.getParent());
        }
        return outputFile.toString();
    }
}
