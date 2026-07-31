package com.everyarrow.dittoe.security;

import com.everyarrow.dittoe.utility.Config;
import com.everyarrow.dittoe.utility.metadata.FileMetadataRepository;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;

class MetadataRepositorySecurityTest {
    @Test
    void rawJsonUpsertRedactsSecretsBeforeWriting() throws Exception {
        String previousBaseDir = Config.BASE_DIR;
        String previousPipelineName = Config.PIPELINE_NAME;
        String previousPipelineStartTime = Config.PIPELINE_START_TIME;
        Path baseDir = Path.of("target", "metadata-repository-security-" + UUID.randomUUID());

        try {
            Config.BASE_DIR = baseDir.toString();
            Config.PIPELINE_NAME = "redaction";
            Config.PIPELINE_START_TIME = "run";

            JsonObject metadata = new JsonObject();
            metadata.addProperty("id", "redaction");
            JsonObject connectionDetails = new JsonObject();
            connectionDetails.addProperty("password", "plain-secret");
            metadata.add("connectionDetails", connectionDetails);

            new FileMetadataRepository().upsert(metadata);

            String stored = Files.readString(baseDir.resolve("redaction").resolve("run").resolve("pmd.json"));
            assertFalse(stored.contains("plain-secret"));
        } finally {
            Config.BASE_DIR = previousBaseDir;
            Config.PIPELINE_NAME = previousPipelineName;
            Config.PIPELINE_START_TIME = previousPipelineStartTime;
        }
    }
}
