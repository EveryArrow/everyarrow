package com.everyarrow.dittoe.pipelines.behaviors.microbatch;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import com.everyarrow.dittoe.io.IOServer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.BehaviorException;
import org.everyarrow.dittoe.schema.Behavior;
import org.everyarrow.dittoe.schema.BehaviorType;
import org.everyarrow.dittoe.schema.PipelineMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Implementation of TargetInsertUpdate behavior. It is a staged behavior. This behavior is used to write
 * data to a resource. Its StageType is always Transmit and Order is always the maximum integer value.
 */
public class MicroBatch extends Behavior {
    public final static String FROM_BATCH = "fromBatch";
    public final static String TO_BATCH = "toBatch";
    public final static String ROW_NUMBER_COLUMN = "rowNumberColumn";
    private final static String CHUNK_SIZE = "chunkSize";
    private final Logger logger = LoggerFactory.getLogger(MicroBatch.class);
    private final PipelineMetadata pipelineMetadata;

    /**
     * Constructor.
     *
     * @param name        Name of the behavior.
     * @param description Description of the behavior.
     * @param properties  Properties of the behavior.
     */
    public MicroBatch(String name, Map<String, String> properties, String description, PipelineMetadata pipelineMetadata) throws BehaviorException {
        super(name, BehaviorType.MicroBatch, properties, description);
        this.pipelineMetadata = pipelineMetadata;
        this.validateProperties();
    }

    public List<Pair<Long, Long>> getBatches() throws BehaviorException {
        this.getPipelineMetadata().getSourceResourceToProcess().getResourceProperties().put("_" + ROW_NUMBER_COLUMN,
                this.getProperties().get(ROW_NUMBER_COLUMN));
        IOServer server = (IOServer) this.getPipelineMetadata().getSourceResourceToProcess().getServer();
        try {
            List<Pair<Long, Long>> chunks = new ArrayList<>();
            List<Long> range = server.getReader().getRange();

            int chunkSize = Integer.parseInt(this.getProperties().get(CHUNK_SIZE));

            for (long i = range.getFirst(); i <= range.getLast(); i += chunkSize) {
                long chunkEnd = Math.min(i + chunkSize - 1, range.getLast());
                chunks.add(new ImmutablePair<>(i, chunkEnd));
            }

            return chunks;
        } catch (Exception e) {
            throw new BehaviorException(e);
        }
    }


    /**
     * Validates properties required to run this behavior.
     *
     * @throws BehaviorException Thrown if any essential property is not found.
     */
    private void validateProperties() throws BehaviorException {
        String[] keys = new String[]{CHUNK_SIZE, ROW_NUMBER_COLUMN};
        Map<String, String> properties = this.getProperties();

        for (String key : keys) {
            if (!properties.containsKey(key))
                throw new BehaviorException("%s property must be provided".formatted(key));
        }
    }

    public PipelineMetadata getPipelineMetadata() {
        return pipelineMetadata;
    }

    public Logger getLogger() {
        return logger;
    }
}
