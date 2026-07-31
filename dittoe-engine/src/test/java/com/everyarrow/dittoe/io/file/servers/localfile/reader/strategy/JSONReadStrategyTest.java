package com.everyarrow.dittoe.io.file.servers.localfile.reader.strategy;

import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.io.data.JavaDataStore;
import com.everyarrow.dittoe.io.file.servers.localfile.LocalFileServer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import com.everyarrow.dittoe.utility.metadata.MetadataRepositoryAPI;
import org.everyarrow.dittoe.schema.Behavior;
import org.everyarrow.dittoe.schema.BehaviorType;
import org.everyarrow.dittoe.schema.DataType;
import org.everyarrow.dittoe.schema.Field;
import org.everyarrow.dittoe.schema.PipelineMetadata;
import org.everyarrow.dittoe.schema.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.everyarrow.dittoe.pipelines.behaviors.microbatch.MicroBatch.ROW_NUMBER_COLUMN;

class JSONReadStrategyTest {
    @TempDir
    Path tempDir;

    @Test
    void readLoadsRowsFromJsonArrayIntoDataStore() throws Exception {
        Files.writeString(tempDir.resolve("people.json"),
                "[{\"name\":\"Ada\",\"age\":42},{\"name\":\"Grace\",\"age\":37}]");

        JSONReadStrategy strategy = createStrategy("people");
        JavaDataStore dataStore = new JavaDataStore("people");
        DataStoreInterface.setDataStoreInterface(dataStore);

        try {
            strategy.read();

            assertEquals(2, dataStore.size());
            assertEquals("Ada", dataStore.get(0).get("name"));
            assertEquals(42.0, dataStore.get(0).get("age"));
            assertEquals("Grace", dataStore.get(1).get("name"));
            assertEquals(37.0, dataStore.get(1).get("age"));
        } finally {
            DataStoreInterface.setDataStoreInterface(null);
        }
    }

    @Test
    void getRangeUsesJsonMicroBatchRowNumberColumn() throws Exception {
        Files.writeString(tempDir.resolve("people.json"),
                "[{\"name\":\"Ada\",\"row_id\":10},{\"name\":\"Grace\",\"row_id\":15}]");

        JSONReadStrategy strategy = createStrategy("people");
        PipelineMetadata pipelineMetadata = new PipelineMetadata();
        pipelineMetadata.setBehaviors(List.of(new Behavior("Micro Batch", BehaviorType.MicroBatch,
                Map.of(ROW_NUMBER_COLUMN, "row_id"), "Split source rows into batches")));
        MetadataRepositoryAPI.setPipelineMetadata(pipelineMetadata);

        try {
            assertEquals(List.of(10L, 15L), strategy.getRange());
        } finally {
            MetadataRepositoryAPI.setPipelineMetadata(null);
        }
    }

    private JSONReadStrategy createStrategy(String resourceName) throws ExecutionException, IOException {
        LocalFileServer server = new LocalFileServer(true);
        server.setConnectionDetails(Map.of("path", tempDir.toString()));

        Resource resource = new Resource();
        resource.setResourceName(resourceName);
        resource.setResourceProperties(new HashMap<>());
        resource.setFields(List.of(
                new Field("name", DataType.String),
                new Field("age", DataType.Decimal)));
        resource.setServer(server);
        server.setResource(resource);

        JSONReadStrategy strategy = new JSONReadStrategy();
        strategy.setReader(server.getReader());
        return strategy;
    }
}
