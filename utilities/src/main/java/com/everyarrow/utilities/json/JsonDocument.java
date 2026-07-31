/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.json;

import com.google.gson.JsonObject;

/**
 * Immutable wrapper around a root JSON object document.
 */
public final class JsonDocument {

    private final JsonObject root;

    private JsonDocument(JsonObject root) {
        this.root = root == null ? new JsonObject() : root.deepCopy();
    }

    public static JsonDocument of(JsonObject root) {
        return new JsonDocument(root);
    }

    public static JsonDocument parse(String json) {
        return new JsonDocument(JsonUtils.parseStrict(json));
    }

    public JsonObject root() {
        return root;
    }

    public JsonReader reader() {
        return new JsonReader(root);
    }

    public String toJson() {
        return JsonUtils.toJson(root);
    }
}
