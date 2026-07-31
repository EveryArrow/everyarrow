/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JsonDocumentTest {

    @Test
    void parseExposesRootObject() {
        JsonDocument document = JsonDocument.parse("{\"name\":\"EveryArrow\"}");

        assertEquals("EveryArrow", document.root().get("name").getAsString());
    }

    @Test
    void readerProvidesNestedAccess() {
        JsonDocument document = JsonDocument.parse("""
                {
                  "profile": {
                    "settings": {
                      "timezone": "America/New_York"
                    }
                  }
                }
                """);

        assertEquals("America/New_York",
                document.reader().string("profile", "settings", "timezone").orElseThrow());
    }

    @Test
    void toJsonRoundTripsRootObject() {
        JsonDocument document = JsonDocument.parse("{\"count\":3,\"active\":true}");
        JsonDocument reparsed = JsonDocument.parse(document.toJson());

        assertEquals(3, reparsed.root().get("count").getAsInt());
        assertTrue(reparsed.root().get("active").getAsBoolean());
    }
}
