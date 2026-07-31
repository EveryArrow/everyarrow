package com.everyarrow.dittoe.utility;

import com.everyarrow.dittoe.io.data.Row;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.everyarrow.dittoe.schema.BehaviorType;
import org.everyarrow.dittoe.schema.DataType;
import org.everyarrow.dittoe.schema.Field;
import org.everyarrow.dittoe.schema.PipelineMetadata;
import org.everyarrow.dittoe.schema.Resource;
import org.everyarrow.dittoe.schema.Server;

import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public final class Utils {
    private static final Gson GSON = new Gson();

    private Utils() {
    }

    public static String getPipelinePath(String pipelineName, String... components) {
        return PipelineFileUtils.getPipelinePath(pipelineName, components);
    }

    public static String removeSuffix(String word, String suffix) {
        if (word == null || word.isEmpty()) {
            return word;
        }

        if (word.toLowerCase().endsWith(suffix)) {
            return word.substring(0, word.length() - suffix.length());
        }

        return word;
    }

    public static String formatTimestampWithTimezone(String timestamp, DateTimeFormatter fromPattern,
                                                     DateTimeFormatter toPattern) throws ExecutionException {
        return TemporalValueUtils.formatTimestampWithTimezone(timestamp, fromPattern, toPattern);
    }

    public static String formatTimestamp(String timestamp, DateTimeFormatter fromPattern,
                                         DateTimeFormatter toPattern) throws ExecutionException {
        return TemporalValueUtils.formatTimestamp(timestamp, fromPattern, toPattern);
    }

    public static boolean isFirstMicroBatch(PipelineMetadata pipelineMetadata) {
        return PipelineMetadataRuntime.isFirstMicroBatch(pipelineMetadata);
    }

    public static boolean isLastMicroBatch(PipelineMetadata pipelineMetadata) {
        return PipelineMetadataRuntime.isLastMicroBatch(pipelineMetadata);
    }

    public static JsonObject sanitize(PipelineMetadata pipelineMetadata) {
        return PipelineMetadataRuntime.sanitize(pipelineMetadata);
    }

    public static boolean shouldGetNewRecords(Map<String, String> properties) {
        return PipelineMetadataRuntime.shouldGetNewRecords(properties);
    }

    public static boolean hasBehavior(PipelineMetadata pmd, BehaviorType behaviorType) {
        return PipelineMetadataRuntime.hasBehavior(pmd, behaviorType);
    }

    public static void clearFile(String path) throws IOException {
        PipelineFileUtils.clearFile(path);
    }

    public static String getInvalidDataFileName(String prefix) {
        return PipelineFileUtils.getInvalidDataFileName(prefix);
    }

    public static int getByteSize(Row row) throws IOException {
        return RowValueUtils.getByteSize(row);
    }

    public static String removeDoubleQuotes(String string) {
        return RowValueUtils.removeDoubleQuotes(string);
    }

    public static Map<String, Integer> getFieldsIndicesMap(List<Field> fields) {
        return RowValueUtils.getFieldsIndicesMap(fields);
    }

    public static String doubleQuote(String string, DataType dataType) {
        return RowValueUtils.doubleQuote(string, dataType);
    }

    public static String singleQuoteIfNotExist(String string) {
        return RowValueUtils.singleQuoteIfNotExist(string);
    }

    public static boolean hasSingleQuotes(String string) {
        return RowValueUtils.hasSingleQuotes(string);
    }

    public static String singleQuote(String string) {
        return RowValueUtils.singleQuote(string);
    }

    public static String doubleQuoteIfNotExist(String string) {
        return RowValueUtils.doubleQuoteIfNotExist(string);
    }

    public static boolean hasDoubleQuotes(String string) {
        return RowValueUtils.hasDoubleQuotes(string);
    }

    public static String doubleQuote(String string) {
        return RowValueUtils.doubleQuote(string);
    }

    public static Gson getGson() {
        return GSON;
    }

    public static String mapToJson(Object object) {
        return getGson().toJson(object);
    }

    public static String addParentheses(String str) {
        return RowValueUtils.addParentheses(str);
    }

    public static boolean useDataStore(PipelineMetadata pipelineMetadata) {
        return PipelineMetadataRuntime.useDataStore(pipelineMetadata);
    }

    public static Server generateServerFromNamespace(String nameSpace) {
        return ServerRuntime.generateServerFromNamespace(nameSpace);
    }

    public static Resource getResourceFromServer(Server server) {
        return ServerRuntime.getResourceFromServer(server);
    }

    public static Object convertToDataType(Object value, DataType dataType) {
        return RowValueUtils.convertToDataType(value, dataType);
    }

    public static void saveMaxNewRecordsFieldValue(Map<String, Object> rowData,
                                                   Map<String, String> resourceProperties) throws ExecutionException {
        RowValueUtils.saveMaxNewRecordsFieldValue(rowData, resourceProperties);
    }

    public static class ProcessRunner {
        private ProcessRunner() {
        }

        public static String run(File workingDirectory, Map<String, String> environment,
                                 String... arguments)
                throws IOException, InterruptedException, ProcessRunnerException {
            return ProcessRunnerSupport.run(workingDirectory, environment, arguments);
        }
    }

    public static class ProcessRunnerException extends Exception {
        public ProcessRunnerException(String message) {
            super(message);
        }
    }
}
