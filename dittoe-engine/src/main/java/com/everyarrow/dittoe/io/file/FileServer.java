package com.everyarrow.dittoe.io.file;
import org.apache.commons.lang3.ObjectUtils;
import com.everyarrow.dittoe.io.IOServer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import com.everyarrow.dittoe.utility.security.SafePathResolver;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;

public abstract class FileServer extends IOServer implements FileServerConnector {

    /**
     * Suffix helps in behaviors like formatting and compression
     * for unique file identification. It is appended to file name's end
     */
    private String suffix = "";


    public FileServer(FileReader fileReader, FileWriter fileWriter) throws ExecutionException {
        super(fileReader, fileWriter);
    }


    @Override
    public void init() throws ExecutionException {
        super.init();
    }

    /**
     * Get the base path when looking for file
     *
     * @return Base path
     */
    public String getBasePath() {
        Map<String, String> resourceProperties = this.getResource().getResourceProperties();
        Map<String, String> serverConnectionDetails = this.getConnectionDetails();
        return ObjectUtils.firstNonNull(resourceProperties.get("basePathOverride"), serverConnectionDetails.get("path"));
    }

    public Path resolveResourcePath(String resourceName) {
        return SafePathResolver.resolveUnderBase(getBasePath(), resourceName);
    }

    /**
     * Get the suffix
     *
     * @return Suffix for this file writer
     */
    public String getSuffix() {
        return suffix;
    }

    /**
     * Set the suffix that will be appended at the end of file name before extension
     *
     * @param suffix to set
     */
    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }

    @Override
    public void close() throws Exception {

    }

    public abstract File downloadFile(String fileName, Path path) throws ExecutionException;
}
