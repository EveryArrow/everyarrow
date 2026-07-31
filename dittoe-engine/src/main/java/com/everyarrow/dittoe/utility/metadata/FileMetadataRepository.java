package com.everyarrow.dittoe.utility.metadata;

import com.everyarrow.dittoe.utility.Config;
import com.everyarrow.dittoe.utility.Utils;
import com.everyarrow.dittoe.utility.security.SecretRedactor;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.everyarrow.dittoe.schema.PipelineMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * File-based metadata repository.
 * Reads and writes pipeline metadata from/to local JSON files.
 */
public class FileMetadataRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(FileMetadataRepository.class);
    private static final Gson GSON = new Gson();
    
    private final String baseDirectory;
    
    public FileMetadataRepository() {
        this.baseDirectory = System.getProperty("user.dir");
        logger.info("Initialized FileMetadataRepository with base directory: {}", baseDirectory);
    }
    
    public FileMetadataRepository(String baseDirectory) {
        this.baseDirectory = baseDirectory;
        logger.info("Initialized FileMetadataRepository with base directory: {}", baseDirectory);
    }
    
    public PipelineMetadata getMetadata(String key) throws MetadataRepositoryException {
        try {
            // Try to find the file - first try with the key as filename
            Path filePath = resolveMetadataFile();
            
            if (!Files.exists(filePath)) {
                throw new MetadataRepositoryException("Metadata file not found: " + filePath);
            }
            
            String jsonContent = Files.readString(filePath);
            JsonObject json = GSON.fromJson(jsonContent, JsonObject.class);
            
            // Extract the pipeline_metadata object from the root JSON
            JsonObject pipelineMetadataJson = json.getAsJsonObject("pipeline_metadata");
            if (pipelineMetadataJson == null) {
                // If no wrapper, assume the entire JSON is the pipeline metadata
                pipelineMetadataJson = json;
            }
            
            PipelineMetadata pm = PipelineMetadataUtils.deSerializePipelineMetadata(pipelineMetadataJson);
            pm.setId(key);
            
            logger.info("Loaded pipeline metadata from file: {}", filePath);
            return pm;
            
        } catch (IOException e) {
            throw new MetadataRepositoryException("Failed to read metadata file for key: " + key, e);
        }
    }
    
    public void upsert(JsonObject pipelineMetadataJson) throws MetadataRepositoryException {
        try {
            String id = pipelineMetadataJson.get("id").getAsString();
            Path filePath = resolveMetadataFile();
            JsonObject sanitizedPipelineMetadataJson = SecretRedactor.redact(pipelineMetadataJson);
            if (filePath.getParent() != null) {
                Files.createDirectories(filePath.getParent());
            }
            
            // Create wrapper object with pipeline_metadata
            JsonObject wrapper = new JsonObject();
            wrapper.addProperty("id", id);
            wrapper.add("pipeline_metadata", sanitizedPipelineMetadataJson);
            
            // Write to file
            String jsonString = GSON.toJson(wrapper);
            Files.writeString(filePath, jsonString);
            
            logger.info("Upserted pipeline metadata file: {}", filePath);
            
        } catch (IOException e) {
            throw new MetadataRepositoryException("Failed to write metadata file", e);
        }
    }
    
    public void upsert(PipelineMetadata pm) throws MetadataRepositoryException {
        upsert(Utils.sanitize(pm));
    }
    
    /**
     * Resolves the file path for a given metadata key.
     * Uses the pipeline directory structure with timestamp.
     */
    private Path resolveMetadataFile() {
        return Paths.get(Utils.getPipelinePath(Config.PIPELINE_NAME, Config.PIPELINE_START_TIME, "pmd.json"));
    }
}
