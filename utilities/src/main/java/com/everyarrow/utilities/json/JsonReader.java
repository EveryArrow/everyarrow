/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.json;

import java.math.BigDecimal;
import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Null-safe helper for traversing nested object-based JSON structures.
 */
public final class JsonReader {

    private final JsonObject root;

    public JsonReader(JsonObject root) {
        this.root = root == null ? new JsonObject() : root;
    }

    public Optional<String> string(String... path) {
        return primitive(path)
                .filter(JsonPrimitive::isString)
                .map(JsonPrimitive::getAsString);
    }

    public Optional<Integer> integer(String... path) {
        return number(path).flatMap(value -> {
            try {
                return Optional.of(value.intValueExact());
            } catch (ArithmeticException ex) {
                return Optional.empty();
            }
        });
    }

    public Optional<Long> longValue(String... path) {
        return number(path).flatMap(value -> {
            try {
                return Optional.of(value.longValueExact());
            } catch (ArithmeticException ex) {
                return Optional.empty();
            }
        });
    }

    public Optional<Boolean> bool(String... path) {
        return primitive(path)
                .filter(JsonPrimitive::isBoolean)
                .map(JsonPrimitive::getAsBoolean);
    }

    public Optional<BigDecimal> decimal(String... path) {
        return number(path);
    }

    public JsonObject objectOrEmpty(String... path) {
        return resolve(path)
                .filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject)
                .orElseGet(JsonObject::new);
    }

    public JsonArray arrayOrEmpty(String... path) {
        return resolve(path)
                .filter(JsonElement::isJsonArray)
                .map(JsonElement::getAsJsonArray)
                .orElseGet(JsonArray::new);
    }

    public boolean hasNonNull(String... path) {
        return resolve(path).isPresent();
    }

    private Optional<JsonPrimitive> primitive(String... path) {
        return resolve(path)
                .filter(JsonElement::isJsonPrimitive)
                .map(JsonElement::getAsJsonPrimitive);
    }

    private Optional<BigDecimal> number(String... path) {
        return primitive(path)
                .filter(JsonPrimitive::isNumber)
                .flatMap(primitive -> {
                    try {
                        return Optional.of(primitive.getAsBigDecimal());
                    } catch (NumberFormatException ex) {
                        return Optional.empty();
                    }
                });
    }

    private Optional<JsonElement> resolve(String... path) {
        JsonElement current = root;
        if (path == null || path.length == 0) {
            return Optional.of(root);
        }

        for (String segment : path) {
            if (segment == null || segment.isBlank() || !current.isJsonObject()) {
                return Optional.empty();
            }

            JsonObject currentObject = current.getAsJsonObject();
            if (!currentObject.has(segment)) {
                return Optional.empty();
            }

            current = currentObject.get(segment);
            if (current == null || current.isJsonNull()) {
                return Optional.empty();
            }
        }

        return Optional.of(current);
    }
}
