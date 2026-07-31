package com.everyarrow.dittoe.pipelines.behaviors.schemavalidation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaValidationTest {

    @Test
    void validationErrorSummaryOmitsRawRejectedValues() {
        String summary = SchemaValidation.summarizeValidationError(
                "ssn: 123-45-6789 is not a correct UUID, email: ada@example.com is not valid");

        assertTrue(summary.contains("ssn"));
        assertTrue(summary.contains("email"));
        assertFalse(summary.contains("123-45-6789"));
        assertFalse(summary.contains("ada@example.com"));
    }
}
