package com.everyarrow.dittoe.utility.metadata;

import com.google.gson.JsonObject;
import org.everyarrow.dittoe.schema.PipelineMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * API for metadata repository operations.
 * Uses file-based storage for pipeline metadata.
 */
public class MetadataRepositoryAPI {
    private static final ThreadLocal<PipelineMetadata> pipelineMetadata = new ThreadLocal<>();
    private static final ThreadLocal<String> apiKey = new ThreadLocal<>();
    private static final Logger logger = LoggerFactory.getLogger(MetadataRepositoryAPI.class);
    
    private final FileMetadataRepository repository;

    /**
     * Creates an instance using file-based metadata repository.
     */
    public MetadataRepositoryAPI() {
        this.repository = new FileMetadataRepository();
        logger.info("Initialized MetadataRepositoryAPI with file-based storage");
    }
    
    /**
     * Creates an instance with a custom base directory.
     */
    public MetadataRepositoryAPI(String baseDirectory) {
        this.repository = new FileMetadataRepository(baseDirectory);
        logger.info("Initialized MetadataRepositoryAPI with base directory: {}", baseDirectory);
    }

    /**
     * @return the thread-local PipelineMetadata, if set
     */
    public static PipelineMetadata getPipelineMetadata() {
        return pipelineMetadata.get();
    }

    /**
     * @param pm the metadata to store in thread-local for later use
     */
    public static void setPipelineMetadata(PipelineMetadata pm) {
        pipelineMetadata.set(pm);
    }

    /**
     * @return the thread-local API key header
     */
    public static String getApiKey() {
        return apiKey.get();
    }

    /**
     * @param key the API key to store in thread-local
     */
    public static void setApiKey(String key) {
        apiKey.set(key);
    }

    /**
     * Fetches the PipelineMetadata for the given id.
     *
     * @param key unique identifier of the metadata document
     * @return deserialized PipelineMetadata
     * @throws MetadataRepositoryException on any failure
     */
    public PipelineMetadata getMetadata(String key) throws MetadataRepositoryException {
        return repository.getMetadata(key);
    }

    /**
     * Upserts the given serialized pipeline metadata JSON in the repository.
     *
     * @param pipelineMetadataJson the {@link JsonObject} representing the pipeline metadata
     * @throws MetadataRepositoryException on any failure
     */
    public void upsert(JsonObject pipelineMetadataJson) throws MetadataRepositoryException {
        repository.upsert(pipelineMetadataJson);
    }

    /**
     * Convenience overload: upserts the given {@link PipelineMetadata}.
     */
    public void upsert(PipelineMetadata pm) throws MetadataRepositoryException {
        repository.upsert(pm);
    }
}
