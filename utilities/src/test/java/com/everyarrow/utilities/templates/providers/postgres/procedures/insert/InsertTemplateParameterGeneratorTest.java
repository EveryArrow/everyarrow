/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.templates.providers.postgres.procedures.insert;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InsertTemplateParameterGeneratorTest {

    @Test
    void excludesDatabaseOwnedColumnsFromInsertContract() {
        Map<String, String> substitutions = InsertTemplateParameterGenerator.generateSubstitutions(
                "campaign",
                List.of("id", "name", "created_at", "computed_score", "identity_code"),
                Map.of(
                        "id", "integer",
                        "name", "text",
                        "created_at", "timestamptz",
                        "computed_score", "numeric",
                        "identity_code", "bigint"),
                Map.of(
                        "id", "nextval('campaign_id_seq'::regclass)",
                        "created_at", "now()"),
                Set.of("computed_score"),
                Set.of("identity_code"));

        assertEquals("\"campaign\"", substitutions.get("TABLE_IDENTIFIER"));
        assertEquals("\"name\", \"created_at\"", substitutions.get("COLUMN_NAMES"));
        assertEquals(
                "x.\"name\", COALESCE(x.\"created_at\", (now())::timestamptz)",
                substitutions.get("COLUMN_SELECT_VALUES"));
        assertEquals("\"name\" text, \"created_at\" timestamptz", substitutions.get("COLUMN_TYPES"));
    }

    @Test
    void preservesExplicitColumnsWhenNoDatabaseOwnedMetadataIsPresent() {
        Map<String, String> substitutions = InsertTemplateParameterGenerator.generateSubstitutions(
                "campaign",
                List.of("id", "name"),
                Map.of("id", "integer", "name", "text"));

        assertEquals("\"id\", \"name\"", substitutions.get("COLUMN_NAMES"));
        assertEquals("x.\"id\", x.\"name\"", substitutions.get("COLUMN_SELECT_VALUES"));
        assertEquals("\"id\" integer, \"name\" text", substitutions.get("COLUMN_TYPES"));
    }

    @Test
    void quotesReservedIdentifiersSeparatelyFromFunctionName() {
        Map<String, String> substitutions = InsertTemplateParameterGenerator.generateSubstitutions(
                "user",
                List.of("id", "user_type"),
                Map.of("id", "integer", "user_type", "text"));

        assertEquals("user", substitutions.get("TABLE_NAME"));
        assertEquals("\"user\"", substitutions.get("TABLE_IDENTIFIER"));
        assertEquals("\"id\", \"user_type\"", substitutions.get("COLUMN_NAMES"));
        assertEquals("x.\"id\", x.\"user_type\"", substitutions.get("COLUMN_SELECT_VALUES"));
    }
}
