package com.everyarrow.dittoe.io.api.servers.bigquery.writer;

import com.google.cloud.bigquery.LegacySQLTypeName;
import com.google.cloud.bigquery.Schema;
import org.everyarrow.dittoe.schema.DataType;
import org.everyarrow.dittoe.schema.Field;
import org.everyarrow.dittoe.schema.SourceToTargetMapping;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.google.cloud.bigquery.Field.Mode.NULLABLE;
import static com.google.cloud.bigquery.Field.Mode.REQUIRED;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BigQueryWriterSchemaTest {

    @Test
    void relaxesMappedFieldsWhenTheSourceIsNullable() {
        Field source = new Field("fileextension", DataType.String, true);
        Field target = new Field("FileExtension", DataType.String, false);
        Schema existing = Schema.of(com.google.cloud.bigquery.Field
                .of("FileExtension", LegacySQLTypeName.STRING)
                .toBuilder()
                .setMode(REQUIRED)
                .build());

        Schema schema = BigQueryWriter.buildCompatibleLoadSchema(
                List.of(target),
                List.of(new SourceToTargetMapping(source, target)),
                existing);

        assertEquals(NULLABLE, schema.getFields().get("FileExtension").getMode());
    }

    @Test
    void relaxesExistingRequiredFieldsThatAreNotInTheMapping() {
        Field source = new Field("subtotal", DataType.Decimal, false);
        Field target = new Field("SubTotal", DataType.Decimal, false);
        Schema existing = Schema.of(
                com.google.cloud.bigquery.Field.of("SubTotal", LegacySQLTypeName.NUMERIC)
                        .toBuilder().setMode(REQUIRED).build(),
                com.google.cloud.bigquery.Field.of("TotalDue", LegacySQLTypeName.NUMERIC)
                        .toBuilder().setMode(REQUIRED).build());

        Schema schema = BigQueryWriter.buildCompatibleLoadSchema(
                List.of(target),
                List.of(new SourceToTargetMapping(source, target)),
                existing);

        assertEquals(REQUIRED, schema.getFields().get("SubTotal").getMode());
        assertEquals(NULLABLE, schema.getFields().get("TotalDue").getMode());
    }

    @Test
    void relaxesRequiredTargetFieldsThatAreNotMapped() {
        Field generatedTarget = new Field("TotalDue", DataType.Decimal, false);
        Schema schema = BigQueryWriter.buildCompatibleLoadSchema(
                List.of(generatedTarget),
                List.of(),
                Schema.of(com.google.cloud.bigquery.Field.of("TotalDue", LegacySQLTypeName.NUMERIC)
                        .toBuilder().setMode(REQUIRED).build()));

        assertEquals(NULLABLE, schema.getFields().get("TotalDue").getMode());
    }
}
