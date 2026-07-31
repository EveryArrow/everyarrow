package com.everyarrow.dittoe.pipelines.behaviors.transformation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DealResultValidatorTest {
    private final DealResultValidator validator = new DealResultValidator();

    @Test
    void widensIntegerToDecimalWithoutUsingDouble() throws Exception {
        Object result = validator.validate(
                field("amount", DealType.DECIMAL, false), 5, "sum(price,1)", 3);

        assertEquals(new BigDecimal("5"), result);
    }

    @Test
    void acceptsNullOnlyForNullableFields() throws Exception {
        assertNull(validator.validate(field("note", DealType.STRING, true), null, "nullif(note,note)", 2));

        ExpressionEvaluationException error = assertThrows(ExpressionEvaluationException.class,
                () -> validator.validate(
                        field("note", DealType.STRING, false), null, "nullif(note,note)", 2));
        assertTrue(error.getMessage().contains("field 'note' at row 2"));
        assertTrue(error.getMessage().contains("non-nullable"));
    }

    @Test
    void rejectsIntegerOverflowAndReportsTheFieldContract() {
        ExpressionEvaluationException error = assertThrows(ExpressionEvaluationException.class,
                () -> validator.validate(
                        field("count", DealType.INTEGER, false), Long.MAX_VALUE, "sum(count,1)", 7));

        assertTrue(error.getMessage().contains("field 'count' at row 7"));
        assertTrue(error.getMessage().contains("expected INTEGER"));
        assertTrue(error.getMessage().contains("actual Long"));
    }

    private static DealSchema.FieldDefinition field(String name, DealType type, boolean nullable) {
        return new DealSchema.FieldDefinition(name, type, nullable, true);
    }
}
