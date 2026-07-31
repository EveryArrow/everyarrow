/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

class JsonReaderTest {

    private final JsonReader reader = new JsonReader(JsonUtils.parseStrict("""
            {
              "name": "EveryArrow",
              "count": 3,
              "total": 42.50,
              "active": true,
              "profile": {
                "displayName": "EA",
                "settings": {
                  "timezone": "America/New_York"
                }
              },
              "nullable": null,
              "items": [
                {"id": 1}
              ]
            }
            """));

    @Test
    void readsScalarsAtTopLevelAndNestedPaths() {
        assertEquals(Optional.of("EveryArrow"), reader.string("name"));
        assertEquals(Optional.of(3), reader.integer("count"));
        assertEquals(Optional.of(new BigDecimal("42.50")), reader.decimal("total"));
        assertEquals(Optional.of(true), reader.bool("active"));
        assertEquals(Optional.of("America/New_York"), reader.string("profile", "settings", "timezone"));
    }

    @Test
    void missingPathReturnsEmptyOptional() {
        assertTrue(reader.string("profile", "missing").isEmpty());
        assertTrue(reader.integer("missing").isEmpty());
    }

    @Test
    void explicitJsonNullReturnsEmptyOptional() {
        assertTrue(reader.string("nullable").isEmpty());
        assertTrue(reader.bool("nullable").isEmpty());
    }

    @Test
    void wrongTypeReturnsEmptyOptional() {
        assertTrue(reader.string("count").isEmpty());
        assertTrue(reader.integer("name").isEmpty());
        assertTrue(reader.bool("profile").isEmpty());
    }

    @Test
    void objectOrEmptyReturnsObjectForExistingObjectAndEmptyForMissingPath() {
        JsonObject profile = reader.objectOrEmpty("profile");
        JsonObject missing = reader.objectOrEmpty("profile", "missing");

        assertEquals("EA", profile.get("displayName").getAsString());
        assertTrue(missing.entrySet().isEmpty());
    }

    @Test
    void arrayOrEmptyReturnsArrayForExistingArrayAndEmptyForMissingPath() {
        JsonArray items = reader.arrayOrEmpty("items");
        JsonArray missing = reader.arrayOrEmpty("profile", "missing");

        assertEquals(1, items.size());
        assertEquals(0, missing.size());
    }

    @Test
    void hasNonNullDistinguishesPresentMissingAndNull() {
        assertTrue(reader.hasNonNull("profile", "displayName"));
        assertFalse(reader.hasNonNull("profile", "missing"));
        assertFalse(reader.hasNonNull("nullable"));
    }
}
