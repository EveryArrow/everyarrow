package com.everyarrow.dittoe.pipelines.execution.abstraction;

import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.pipelines.behaviors.StagedBehaviorBase;
import com.everyarrow.dittoe.pipelines.behaviors.StagedBehaviorInput;
import com.everyarrow.dittoe.utility.security.SafePathResolver;
import org.everyarrow.dittoe.schema.BehaviorType;
import org.everyarrow.dittoe.schema.PipelineMetadata;
import org.everyarrow.dittoe.schema.StageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


public abstract class Cache extends StagedBehaviorBase {
    private final Logger logger = LoggerFactory.getLogger(Cache.class);
    private final String cacheErrorFile;
    private PrintWriter writer;
    private boolean cacheLoaded;

    public Cache(String name, String description, Integer order, Map<String, String> properties,
                 PipelineMetadata pipelineMetadata, DataStoreInterface dataStore) {
        super(new StagedBehaviorInput(name, BehaviorType.Cache, properties, description,
                Optional.ofNullable(order).orElse(Integer.MIN_VALUE), StageType.Process, pipelineMetadata, dataStore));

        this.cacheErrorFile = properties.get("cacheErrorFile");
    }

    protected void writeErrorRow(Map<String, Object> row, String error) throws IOException {
        if (this.getCacheErrorFile() == null)
            return;

        String rowValue = this.getPipelineMetadata()
                .getSourceResourceToProcess()
                .getFields()
                .stream()
                .map(field -> Optional.ofNullable(row.get(field.getName()))
                        .map(value -> "\"" + value + "\"")
                        .orElse("\"\\\\N\""))
                .collect(Collectors.joining(","));

        this.getWriter().printf("%s,\"%s\"%n", rowValue, error);
    }

    @Override
    public void exec() throws BehaviorException {
        try {
            // load cache
            if (!this.isCacheLoaded()) {
                this.loadCache();
                this.setCacheLoaded();
            }

            int invalidRowsCount = 0;

            int dataStoreSize = this.getDataStore().size();
            // resolve cache
            for (int i = 0; i < dataStoreSize; i++) {
                Map<String, Object> row = this.getDataStore().get(i);

                String error = this.resolveCache(row);

                if (error != null) {
                    invalidRowsCount++;
                    this.getDataStore().setError(i, error);
                } else
                    this.getDataStore().replace(i, row);
            }

            this.getLogger().info("Rows with cache error: {}", invalidRowsCount);
            this.getLogger().info("Rows without cache error: {}", dataStoreSize - invalidRowsCount);
            this.getLogger().info("Cache error percentage: {}", (invalidRowsCount * 100.0) / dataStoreSize);
        } catch (Exception e) {
            throw new BehaviorException(e);
        } finally {
            this.closeErrorWriter();
        }
    }

    protected void closeErrorWriter() {
        if (this.writer != null)
            this.writer.close();
    }

    protected PrintWriter getWriter() throws IOException {
        if (this.writer == null) {
            Path file = SafePathResolver.resolveAllowedPath(this.getCacheErrorFile(), "cacheErrorFile");
            boolean fileAlreadyExists = Files.isRegularFile(file);
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }

            this.writer = new PrintWriter(Files.newBufferedWriter(file,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND));

            if (!fileAlreadyExists) {
                // write headers
                String headers = this.getPipelineMetadata().getSourceResourceToProcess()
                        .getFields().stream().map(field -> "\"" + field.getName() + "\"").collect(Collectors.joining(","));
                this.getWriter().printf("%s,\"error\"%n", headers);
            }
        }

        return writer;
    }

    protected abstract void loadCache() throws BehaviorException;

    protected abstract String resolveCache(Map<String, Object> row) throws BehaviorException;

    protected String getCacheErrorFile() {
        return cacheErrorFile;
    }

    protected boolean isCacheLoaded() {
        return cacheLoaded;
    }

    protected void setCacheLoaded() {
        this.cacheLoaded = true;
    }

    public Logger getLogger() {
        return logger;
    }
}
