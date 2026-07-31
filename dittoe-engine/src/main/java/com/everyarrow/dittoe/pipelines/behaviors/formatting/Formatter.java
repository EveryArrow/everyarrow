package com.everyarrow.dittoe.pipelines.behaviors.formatting;

import com.everyarrow.dittoe.io.data.DataStoreException;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.io.file.FileType;
import com.everyarrow.dittoe.pipelines.behaviors.StagedBehaviorBase;
import com.everyarrow.dittoe.pipelines.behaviors.StagedBehaviorInput;
import com.everyarrow.dittoe.pipelines.execution.abstraction.BehaviorException;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.BehaviorType;
import org.everyarrow.dittoe.schema.PipelineMetadata;
import org.everyarrow.dittoe.schema.Resource;
import org.everyarrow.dittoe.schema.StageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class Formatter extends StagedBehaviorBase {
    private final Logger logger = LoggerFactory.getLogger(Formatter.class);
    private final String fadsType = this.getPipelineMetadata().getTargetResourceToProcess().getServer().getType().name();
    private FileType fileType;

    public Formatter(String name, String description, Map<String, String> properties, PipelineMetadata pipelineMetadata, DataStoreInterface dataStore) {
        super(new StagedBehaviorInput(name,
                BehaviorType.Formatting, properties, description, Integer.MAX_VALUE,
                StageType.Process, pipelineMetadata, dataStore));
        Resource targetResource = this.getPipelineMetadata().getTargetResourceToProcess();
        if (fadsType.startsWith("FILE")) {
            try {
                this.fileType = FileType.getFileType(targetResource.getResourceProperties());
            } catch (ExecutionException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    @Override
    public void exec() throws BehaviorException {
        try {
            final PipelineMetadata metadata = this.getPipelineMetadata();


            switch (fadsType) {
                case "FILE":
                    execFile();
                    break;

                default:
                    this.getLogger().info("No need to format non-file based data");
                    break;
            }
        } catch (Exception e) {
            throw new BehaviorException(e);
        }
    }

    private void execFile() throws ExecutionException {
        final DataStoreInterface store = this.getDataStore();
        final PipelineMetadata metadata = this.getPipelineMetadata();

        switch (this.fileType) {
            case FileType.PROTOCOL_BUFFER:
                // Unfinished
                try {
                    ProtoBuffFormatter.generateProtoFile(metadata, store);
                } catch (DataStoreException e) {
                    throw new ExecutionException(e);
                }
                break;

            case FileType.AVRO:
                AvroFormatter.generateSchema(metadata, store);
                break;

            default:
//                IOServer server = (IOServer) metadata.getTargetResourceToProcess().getServer();
//                try (StreamWriter writer =  server.getWriter(metadata) ) {
//                    writer.setSuffix("-formatted");
//                    writer.write(this.getDataStore(), this.getPipelineMetadata().getS2TMap());
//                } catch (Exception ex) {
//                    throw new ExecutionException(ex);
//                }
                break;
        }
    }


    private Logger getLogger() {
        return logger;
    }
}
