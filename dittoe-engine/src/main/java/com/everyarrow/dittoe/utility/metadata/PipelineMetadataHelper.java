package com.everyarrow.dittoe.utility.metadata;

import com.everyarrow.utilities.security.CryptoUtils;
import com.google.gson.JsonObject;
import com.everyarrow.dittoe.io.IOServer;
import org.everyarrow.dittoe.schema.*;
import com.everyarrow.dittoe.utility.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.util.List;
import java.util.Optional;


public class PipelineMetadataHelper {
    private static final Logger logger = LoggerFactory.getLogger(PipelineMetadataHelper.class);
    private static SecretKey secretKey;

    public static void syncFields(PipelineMetadata pipelineMetadata) throws Exception {
//        pipelineMetadata.setTemplateType(TemplateType.valueOf(pipelineMetadata.getSourceResourceToProcess().getServer().getType()
//                + "To" + pipelineMetadata.getTargetResourceToProcess().getServer().getType()));


        List<Field> fields = ((IOServer) pipelineMetadata.getSourceResourceToProcess().getServer()).getReader().getFields();

        pipelineMetadata.getSourceResourceToProcess().setFields(fields);
        pipelineMetadata.getTargetResourceToProcess().setFields(fields);

        pipelineMetadata.setS2TMap(
                fields.stream().map(field -> new SourceToTargetMapping(field, field)).toList()
        );

        if (pipelineMetadata.getBehaviors().stream().noneMatch(behavior -> behavior.getBehaviorType() == BehaviorType.SourceRetrieval)) {
            Behavior sourceRetrieval = new Behavior();
            sourceRetrieval.setName("Source Retrieval");
            sourceRetrieval.setDescription("For retrieving data from the source resource");
            sourceRetrieval.setBehaviorType(BehaviorType.SourceRetrieval);

            pipelineMetadata.getBehaviors().add(sourceRetrieval);
        }

        if (pipelineMetadata.getBehaviors().stream().noneMatch(behavior -> behavior.getBehaviorType() == BehaviorType.TargetInsertUpdate)) {
            Behavior targetInsertUpdate = new Behavior();
            targetInsertUpdate.setName("Target Insert Update");
            targetInsertUpdate.setDescription("For writing data to the target resource");
            targetInsertUpdate.setBehaviorType(BehaviorType.TargetInsertUpdate);

            pipelineMetadata.getBehaviors().add(targetInsertUpdate);
        }
    }


    // save the current micro batch
    public static void saveCurrentMicroBatch(PipelineMetadata pipelineMetadata) throws MetadataRepositoryException {
        logger.info("Saving micro batch in Pipeline Metadata");

        JsonObject sanitized = Utils.sanitize(pipelineMetadata);

        new MetadataRepositoryAPI().upsert(sanitized);
    }

    public static void saveNewRecordsFieldValue(PipelineMetadata pipelineMetadata) throws MetadataRepositoryException {
        // do not save new records field max value if new records only is not enabled or
        // if micro batching is enabled, and it is not the last batch
        if (!Utils.shouldGetNewRecords(pipelineMetadata.getSourceResourceToProcess().getResourceProperties()) ||
                !Utils.isLastMicroBatch(pipelineMetadata))
            return;

        JsonObject sanitized = Utils.sanitize(pipelineMetadata);

        JsonObject properties = sanitized.getAsJsonObject("sourceResourceToProcess")
                .getAsJsonObject("resourceProperties");

        logger.info("Saving new records field max value in Pipeline Metadata");
        logger.info("New Records Field: {}", properties.get("newRecordsField"));
        logger.info("New Records Field Value: {}", properties.get("newRecordsFieldValue"));

        new MetadataRepositoryAPI().upsert(sanitized);
    }

    // lazy load
    public static SecretKey getSecretKey() {
        if (secretKey == null) {
            secretKey = Optional.ofNullable(System.getenv("SECRET_KEY"))
                    .map(CryptoUtils::createKeyFromString)
                    .orElseThrow(() -> new IllegalArgumentException("Environment variable SECRET_KEY not set"));
        }

        return secretKey;
    }
}
