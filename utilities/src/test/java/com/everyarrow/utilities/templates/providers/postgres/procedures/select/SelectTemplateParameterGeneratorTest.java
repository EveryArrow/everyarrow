/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.templates.providers.postgres.procedures.select;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SelectTemplateParameterGeneratorTest {

    @Test
    void quotesReservedAndHostileIdentifiers() {
        Map<String, String> substitutions =
                SelectTemplateParameterGenerator.generateSubstitutions(
                        "user\"; DROP TABLE audit; --",
                        List.of("select", "display\"name", "ignored"),
                        List.of("ignored"),
                        "select",
                        Map.of("select", "uuid"));

        assertEquals(
                "\"user\"\"; DROP TABLE audit; --\"",
                substitutions.get("TABLE_IDENTIFIER"));
        assertEquals(
                "\"user\"\"; drop table audit; --_select_one\"",
                substitutions.get("FUNCTION_IDENTIFIER"));
        assertEquals(
                "\"select\", \"display\"\"name\"",
                substitutions.get("COLUMN_NAMES"));
        assertEquals(
                "\"user\"\"; DROP TABLE audit; --\".\"select\""
                        + " = (p_data->>'select')::uuid",
                substitutions.get("WHERE_CLAUSE"));
    }

    @Test
    void escapesPrimaryKeyUsedAsJsonLiteral() {
        Map<String, String> substitutions =
                SelectTemplateParameterGenerator.generateSubstitutions(
                        "orders",
                        List.of("customer'id"),
                        List.of(),
                        "customer'id",
                        Map.of("customer'id", "bigint"));

        assertEquals(
                "\"orders\".\"customer'id\" = (p_data->>'customer''id')::bigint",
                substitutions.get("WHERE_CLAUSE"));
    }

    @Test
    void preservesPostgresCaseFoldingForGeneratedFunctionNames() {
        Map<String, String> substitutions =
                SelectTemplateParameterGenerator.generateSubstitutions(
                        "OrderLine",
                        List.of("Id"),
                        List.of(),
                        "Id",
                        Map.of("Id", "bigint"));

        assertEquals(
                "\"orderline_select_one\"",
                substitutions.get("FUNCTION_IDENTIFIER"));
        assertEquals(
                "\"OrderLine\".\"Id\" = (p_data->>'Id')::bigint",
                substitutions.get("WHERE_CLAUSE"));
    }

    @Test
    void rejectsMissingPrimaryKeyType() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SelectTemplateParameterGenerator.generateSubstitutions(
                        "orders",
                        List.of("id"),
                        List.of(),
                        "id",
                        Map.of()));
    }
}
