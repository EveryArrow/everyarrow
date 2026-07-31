package com.everyarrow.dittoe.pipelines.behaviors.transformation;

import com.everyarrow.dittoe.io.data.JavaDataStore;
import com.everyarrow.dittoe.pipelines.execution.PipelineExecutionStats;
import com.everyarrow.dittoe.pipelines.execution.abstraction.BehaviorException;
import org.everyarrow.dittoe.schema.DataType;
import org.everyarrow.dittoe.schema.Field;
import org.everyarrow.dittoe.schema.PipelineMetadata;
import org.everyarrow.dittoe.schema.Resource;
import org.everyarrow.dittoe.schema.SourceField;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransformationTest {
    @Test
    void evaluatesComputedFieldsAgainstThePmdResourceAndInjectedDataStore() throws Exception {
        Resource sourceResource = new Resource();
        sourceResource.setFields(List.of(
                new Field("price", DataType.Integer),
                new SourceField("total", DataType.Integer, "sum(subtotal,2)"),
                new SourceField("subtotal", DataType.Integer, "sum(price,1)")));
        PipelineMetadata pipelineMetadata = new PipelineMetadata();
        pipelineMetadata.setSourceResourceToProcess(sourceResource);

        JavaDataStore dataStore = new JavaDataStore("transformation-test");
        dataStore.add(0, Map.of("price", 4));

        Transformation transformation = new Transformation(
                "Transformation", "", Map.of(), pipelineMetadata, dataStore);
        transformation.setStageStats(new PipelineExecutionStats.StageStats("Transformation"));

        transformation.exec();

        assertEquals(Map.of("price", 4, "subtotal", 5, "total", 7), dataStore.get(0));
    }

    @Test
    void validatesAndNormalizesComputedResultsUsingThePmdFieldType() throws Exception {
        Resource sourceResource = new Resource();
        sourceResource.setFields(List.of(
                new Field("price", DataType.Integer),
                new SourceField("total", DataType.Decimal, "sum(price,1)")));
        PipelineMetadata pipelineMetadata = new PipelineMetadata();
        pipelineMetadata.setSourceResourceToProcess(sourceResource);
        JavaDataStore dataStore = new JavaDataStore("decimal-result-test");
        dataStore.add(0, Map.of("price", 4));

        Transformation transformation = transformation(pipelineMetadata, dataStore);
        transformation.exec();

        assertEquals(new BigDecimal("5"), dataStore.get(0).get("total"));
    }

    @Test
    void rejectsAnIncompatibleComputedTypeBeforeEvaluatingRows() throws Exception {
        Resource sourceResource = new Resource();
        sourceResource.setFields(List.of(
                new Field("price", DataType.Integer),
                new Field("name", DataType.String),
                new SourceField("subtotal", DataType.Integer, "sum(price,1)"),
                new SourceField("invalid", DataType.Integer, "upper(name)")));
        PipelineMetadata pipelineMetadata = new PipelineMetadata();
        pipelineMetadata.setSourceResourceToProcess(sourceResource);
        JavaDataStore dataStore = new JavaDataStore("atomic-row-test");
        Map<String, Object> original = Map.of("price", 4, "name", "andy");
        dataStore.add(0, original);

        Transformation transformation = transformation(pipelineMetadata, dataStore);
        BehaviorException error = assertThrows(BehaviorException.class, transformation::exec);

        assertEquals(original, dataStore.get(0));
        assertEquals(0, transformation.getStageStats().getRowsProcessed());
        assertEquals(1, transformation.getStageStats().getRowsErrored());
        assertTrue(error.getCause().getMessage().contains("Failed to type-check computed DEAL field 'invalid'"));
        assertTrue(error.getCause().getMessage().contains("has type STRING"));
        assertTrue(error.getCause().getMessage().contains("declares INTEGER"));
        assertTrue(error.getCause().getMessage().contains("to_integer"));
    }

    @Test
    void permitsNullForANullableComputedField() throws Exception {
        Resource sourceResource = new Resource();
        sourceResource.setFields(List.of(
                new Field("name", DataType.String),
                new SourceField("alias", DataType.String, true, "nullif(name,name)")));
        PipelineMetadata pipelineMetadata = new PipelineMetadata();
        pipelineMetadata.setSourceResourceToProcess(sourceResource);
        JavaDataStore dataStore = new JavaDataStore("nullable-result-test");
        dataStore.add(0, Map.of("name", "andy"));

        Transformation transformation = transformation(pipelineMetadata, dataStore);
        transformation.exec();

        Map<String, Object> expected = new HashMap<>();
        expected.put("name", "andy");
        expected.put("alias", null);
        assertEquals(expected, dataStore.get(0));
    }

    @Test
    void explicitlyConvertsSourceResourceValuesToTheComputedPmdType() throws Exception {
        Resource sourceResource = new Resource();
        sourceResource.setFields(List.of(
                new Field("raw_quantity", DataType.String),
                new SourceField("quantity", DataType.Integer, "to_integer(raw_quantity)")));
        PipelineMetadata pipelineMetadata = new PipelineMetadata();
        pipelineMetadata.setSourceResourceToProcess(sourceResource);
        JavaDataStore dataStore = new JavaDataStore("conversion-result-test");
        dataStore.add(0, Map.of("raw_quantity", "42"));

        Transformation transformation = transformation(pipelineMetadata, dataStore);
        transformation.exec();

        assertEquals(Map.of("raw_quantity", "42", "quantity", 42), dataStore.get(0));
    }

    private static Transformation transformation(PipelineMetadata pipelineMetadata, JavaDataStore dataStore) {
        Transformation transformation = new Transformation(
                "Transformation", "", Map.of(), pipelineMetadata, dataStore);
        transformation.setStageStats(new PipelineExecutionStats.StageStats("Transformation"));
        return transformation;
    }
}
