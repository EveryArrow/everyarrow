package com.everyarrow.dittoe.io.file;

import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

import java.util.Locale;
import java.util.Map;

/**
 * Enum representing File types.
 */
public enum FileType {
    PROTOCOL_BUFFER,
    AVRO,
    CSV,
    JSON;

    /**
     * To get the type of file.
     *
     * @param properties Map containing file type property.
     * @return Type of the FIle.
     * @throws ExecutionException Thrown if the file type is not supported.
     */
    public static FileType getFileType(Map<String, String> properties) throws ExecutionException {
        validateFileTypeProperty(properties);
        String fileType = properties.get("fileType").toLowerCase(Locale.ROOT);
        return FileType.valueOf(fileType.toUpperCase());
    }

    /**
     * Validates if fileType property exists in the input Map or not.
     *
     * @param properties Map containing properties.
     * @throws ExecutionException Thrown if fileType property is missing.
     */
    public static void validateFileTypeProperty(Map<String, String> properties) throws ExecutionException {
        if (!properties.containsKey("fileType"))
            throw new ExecutionException("fileType property must be provided");
    }
}
