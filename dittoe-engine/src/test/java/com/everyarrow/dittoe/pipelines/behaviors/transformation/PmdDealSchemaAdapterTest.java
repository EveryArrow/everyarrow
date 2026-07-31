package com.everyarrow.dittoe.pipelines.behaviors.transformation;

import org.everyarrow.dittoe.schema.DataType;
import org.everyarrow.dittoe.schema.Field;
import org.everyarrow.dittoe.schema.SourceField;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PmdDealSchemaAdapterTest {
    @Test
    void translatesPmdResourceFieldsAtTheTransformationBoundary() {
        DealSchema schema = PmdDealSchemaAdapter.fromResourceFields(List.of(
                new Field("amount", DataType.Decimal, true),
                new SourceField("total", DataType.Decimal, "sum(amount,1)")));

        DealSchema.FieldDefinition amount = schema.requireField("amount");
        assertEquals(DealType.DECIMAL, amount.dataType());
        assertTrue(amount.nullable());
        assertFalse(amount.computed());

        DealSchema.FieldDefinition total = schema.requireField("total");
        assertEquals(DealType.DECIMAL, total.dataType());
        assertFalse(total.nullable());
        assertTrue(total.computed());
    }
}
