/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;

class JsonArrayViewTest {

    @Test
    void sizeAndIsEmptyReflectMappedObjectElements() {
        JsonArray array = JsonUtils.parseStrict("""
                {
                  "items": [
                    {"name": "first"},
                    {"name": "second"}
                  ]
                }
                """).getAsJsonArray("items");
        JsonArrayView<String> view = new JsonArrayView<>(array, object -> object.get("name").getAsString());

        assertEquals(2, view.size());
        assertFalse(view.isEmpty());
    }

    @Test
    void streamMapsObjectElements() {
        JsonArray array = JsonUtils.parseStrict("""
                {
                  "items": [
                    {"name": "first"},
                    {"name": "second"}
                  ]
                }
                """).getAsJsonArray("items");
        JsonArrayView<String> view = new JsonArrayView<>(array, object -> object.get("name").getAsString());

        assertEquals(List.of("first", "second"), view.stream().toList());
    }

    @Test
    void nonObjectElementsAreSkippedIntentionally() {
        JsonArray array = new JsonArray();
        array.add(JsonUtils.parseStrict("{\"name\":\"first\"}"));
        array.add(new JsonPrimitive("ignored"));
        array.add(new JsonPrimitive(3));

        JsonArrayView<String> view = new JsonArrayView<>(array, object -> object.get("name").getAsString());

        assertEquals(1, view.size());
        assertEquals(List.of("first"), view.stream().toList());
    }

    @Test
    void nullArrayBehavesAsEmptyView() {
        JsonArrayView<String> view = new JsonArrayView<>(null, object -> object.get("name").getAsString());

        assertTrue(view.isEmpty());
        assertEquals(0, view.size());
    }
}
