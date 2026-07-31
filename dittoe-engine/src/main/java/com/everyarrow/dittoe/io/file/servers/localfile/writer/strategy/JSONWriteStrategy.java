package com.everyarrow.dittoe.io.file.servers.localfile.writer.strategy;

import com.everyarrow.dittoe.io.WriteStrategy;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.io.file.Extension;
import com.everyarrow.dittoe.io.file.servers.localfile.writer.LocalFileWriter;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import com.google.gson.Gson;
import org.everyarrow.dittoe.schema.Field;
import org.everyarrow.dittoe.schema.PipelineMetadata;
import org.everyarrow.dittoe.schema.SourceToTargetMapping;
import com.everyarrow.dittoe.utility.Batch;
import com.everyarrow.dittoe.utility.Chunk;
import com.everyarrow.dittoe.utility.metadata.MetadataRepositoryAPI;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

public class JSONWriteStrategy extends WriteStrategy {
    private static final Gson GSON = new Gson();

    public JSONWriteStrategy() throws ExecutionException {
        super();
    }

    @Override
    public void write() throws ExecutionException {
        DataStoreInterface dataStore = DataStoreInterface.getDataStoreInterface();
        PipelineMetadata pipelineMetadata = MetadataRepositoryAPI.getPipelineMetadata();

        if (dataStore.size() == 0)
            return;

        Path fileName = getJsonFilePath();

        try {
            this.clearFile(fileName);
        } catch (IOException e) {
            throw new ExecutionException(e);
        }
        try (BufferedWriter bufferedWriter =
                     new BufferedWriter(new java.io.FileWriter(fileName.toFile(), true))) {
            bufferedWriter.append("[");
            bufferedWriter.flush();
            int chunkSize = 100000;
            List<Chunk> chunks = Batch.divideIntoChunks(dataStore, chunkSize);
            List<Callable<Object>> callables = this.getCallables(dataStore, chunks, pipelineMetadata.getS2TMap(), pipelineMetadata);
            for (Callable<Object> func : callables)
                func.call();
            bufferedWriter.append("]");
            bufferedWriter.flush();
        } catch (Exception e) {
            throw new ExecutionException("Could not write data", e);
        }
    }

    /**
     * Creates and returns a list containing callables.
     * Each callable would insert a portion of data in the csv resource.
     *
     * @param dataStore              DataStore object.
     * @param chunks                 List of chunks indices into which total rows are divided.
     * @param sourceToTargetMappings Mapping of source to target fields.
     * @return List of callables.
     */
    private List<Callable<Object>> getCallables(DataStoreInterface dataStore, List<Chunk> chunks,
                                                List<SourceToTargetMapping> sourceToTargetMappings, PipelineMetadata pipelineMetadata) {
        List<Callable<Object>> callables = new LinkedList<>();
        int dataStoreSize = dataStore.size();
        AtomicInteger overallIndex = new AtomicInteger(0);
        Path fileName = getJsonFilePath();
        for (Chunk chunkIndices : chunks) {
            Callable<Object> callable = () -> {
                try (BufferedWriter bufferedWriter =
                             new BufferedWriter(new java.io.FileWriter(fileName.toFile(), true))) {
                    for (int i = chunkIndices.startIndex(); i <= chunkIndices.endIndex(); i++) {
                        bufferedWriter.append(this.getMappedData(dataStore.get(i), sourceToTargetMappings));
                        if (overallIndex.getAndIncrement() < dataStoreSize - 1)
                            bufferedWriter.append(",");
                    }
                }
                return null;
            };
            callables.add(callable);
        }
        return callables;
    }

    /**
     * Applies source to target mapping to the data.
     *
     * @param data                   The input data.
     * @param sourceToTargetMappings Mapping of source to target fields.
     * @return Mapped data.
     */
    private String getMappedData(Map<String, Object> data, List<SourceToTargetMapping> sourceToTargetMappings) {
        StringBuilder stringBuilder = new StringBuilder("{");
        int i = 0;
        for (SourceToTargetMapping sourceToTargetMapping : sourceToTargetMappings) {
            Field sourceField = sourceToTargetMapping.getSourceField();
            Field targetField = sourceToTargetMapping.getTargetField();
            Object value = data.get(sourceField.getName());
            stringBuilder.append(GSON.toJson(targetField.getName()));
            stringBuilder.append(":");
            stringBuilder.append(value == null ? "null" : GSON.toJson(value));
            if (i++ < sourceToTargetMappings.size() - 1) {
                stringBuilder.append(",");
            }
        }
        stringBuilder.append("}");
        return stringBuilder.toString();
    }

    @Override
    public LocalFileWriter getWriter() {
        return (LocalFileWriter) super.getWriter();
    }

    /**
     * Truncates the specified file if it exists.
     *
     * @param path The path to file.
     * @throws IOException Thrown in case any error occurs while truncating the file.
     */
    public void clearFile(Path path) throws IOException {
        File file = path.toFile();
        if (file.exists())
            FileChannel.open(path, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING).close();
    }

    private Path getJsonFilePath() {
        return this.getWriter().getServer().resolveResourcePath(
                this.getWriter().getServer().getResource().getResourceName()
                        .concat(this.getWriter().getServer().getSuffix())
                        .concat(Extension.JSON));
    }
}
