/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.templates.providers.postgres.procedures.delete;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeleteTemplateParameterGeneratorTest {

    @Test
    void softDeletesTablesWithIsActiveRow() {
        Map<String, String> substitutions = DeleteTemplateParameterGenerator.generateSubstitutions(
                "principal_role_assignment",
                List.of("id", "principal_id", "is_active_row"),
                "id",
                Map.of(
                        "id", "uuid",
                        "principal_id", "uuid",
                        "is_active_row", "bool"));

        assertEquals("Record soft-deleted successfully", substitutions.get("SUCCESS_MESSAGE"));
        assertTrue(substitutions.get("DELETE_STATEMENT").contains("SET \"is_active_row\" = false"));
    }

    @Test
    void preservesHardDeleteForTablesWithoutSoftDeleteColumn() {
        Map<String, String> substitutions = DeleteTemplateParameterGenerator.generateSubstitutions(
                "service_call_log",
                List.of("id", "service_type"),
                "id",
                Map.of(
                        "id", "uuid",
                        "service_type", "text"));

        assertEquals("Record deleted successfully", substitutions.get("SUCCESS_MESSAGE"));
        assertTrue(substitutions.get("DELETE_STATEMENT").startsWith("DELETE FROM \"service_call_log\""));
    }
}
