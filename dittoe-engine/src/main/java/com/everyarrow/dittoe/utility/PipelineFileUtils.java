package com.everyarrow.dittoe.utility;

import com.everyarrow.dittoe.utility.security.SafePathResolver;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

final class PipelineFileUtils {
    private static final String INVALID_DATA_FILE_POST_FIX = "_invalid_rows.dat";

    private PipelineFileUtils() {
    }

    static String getPipelinePath(String pipelineName, String... components) {
        return SafePathResolver.resolvePipelinePath(pipelineName, components).toString();
    }

    static void clearFile(String path) throws IOException {
        File file = new File(path);
        if (file.exists()) {
            FileChannel.open(Path.of(path), StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING).close();
        }
    }

    static String getInvalidDataFileName(String prefix) {
        return prefix.concat(INVALID_DATA_FILE_POST_FIX);
    }
}
