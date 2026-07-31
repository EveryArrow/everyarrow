/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.templates.providers.postgres.procedures.update;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateTemplateParameterGeneratorTest {

    @Test
    void updatesIsActiveRowTablesInPlace() {
        Map<String, String> substitutions = UpdateTemplateParameterGenerator.generateSubstitutions(
                "principal_role_assignment",
                List.of("id", "role_id", "is_active_row"),
                "id",
                Map.of(
                        "id", "uuid",
                        "role_id", "uuid",
                        "is_active_row", "bool"));

        assertTrue(substitutions.get("UPDATE_STATEMENT").contains("\"is_active_row\" = x.\"is_active_row\""));
        assertFalse(substitutions.get("INSERT_STATEMENT").contains("INSERT INTO"));
    }

    @Test
    void preservesLegacyIsValidReplacementBehavior() {
        Map<String, String> substitutions = UpdateTemplateParameterGenerator.generateSubstitutions(
                "legacy_table",
                List.of("id", "name", "isValid"),
                "id",
                Map.of(
                        "id", "uuid",
                        "name", "text",
                        "isValid", "bool"));

        assertTrue(substitutions.get("UPDATE_STATEMENT").contains("SET \"isValid\" = false"));
        assertTrue(substitutions.get("INSERT_STATEMENT").contains("INSERT INTO \"legacy_table\""));
    }

    @Test
    void quotesReservedColumnNames() {
        Map<String, String> substitutions = UpdateTemplateParameterGenerator.generateSubstitutions(
                "productproductphoto",
                List.of("productid", "primary"),
                "productid",
                Map.of("productid", "int4", "primary", "boolean"));

        assertTrue(substitutions.get("UPDATE_STATEMENT").contains(
                "SET \"primary\" = x.\"primary\""));
        assertTrue(substitutions.get("UPDATE_STATEMENT").contains(
                "AS x(\"productid\" int4, \"primary\" boolean)"));
    }
}
