/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.db;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetadataUtilsTest {

    @Test
    void identifiesDatabaseOwnedInsertColumns() {
        MetadataUtils.TableMetadata metadata = new MetadataUtils.TableMetadata(
                List.of("id", "name", "created_at", "search_vector", "identity_code"),
                List.of("id"),
                Map.of(
                        "id", "integer",
                        "name", "text",
                        "created_at", "timestamptz",
                        "search_vector", "tsvector",
                        "identity_code", "bigint"),
                Map.of(
                        "id", new MetadataUtils.ColumnMetadata(
                                "id",
                                "integer",
                                "nextval('campaign_id_seq'::regclass)",
                                false,
                                false),
                        "name", new MetadataUtils.ColumnMetadata(
                                "name",
                                "text",
                                null,
                                false,
                                false),
                        "created_at", new MetadataUtils.ColumnMetadata(
                                "created_at",
                                "timestamptz",
                                "now()",
                                false,
                                false),
                        "search_vector", new MetadataUtils.ColumnMetadata(
                                "search_vector",
                                "tsvector",
                                null,
                                true,
                                false),
                        "identity_code", new MetadataUtils.ColumnMetadata(
                                "identity_code",
                                "bigint",
                                null,
                                false,
                                true)));

        assertEquals(
                Set.of("id", "search_vector", "identity_code"),
                metadata.getDatabaseOwnedInsertColumns());
        assertEquals(List.of("name", "created_at"), metadata.getInsertInputColumns());
    }
}
