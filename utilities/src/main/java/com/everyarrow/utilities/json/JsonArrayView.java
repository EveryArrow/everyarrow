/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.json;

import java.util.Iterator;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Lightweight object-focused view over a JSON array.
 * Non-object elements are skipped.
 */
public final class JsonArrayView<T> implements Iterable<T> {

    private final JsonArray array;
    private final Function<JsonObject, T> mapper;

    public JsonArrayView(JsonArray array, Function<JsonObject, T> mapper) {
        this.array = array == null ? new JsonArray() : array;
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    public Stream<T> stream() {
        return StreamSupport.stream(array.spliterator(), false)
                .filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject)
                .map(mapper);
    }

    public boolean isEmpty() {
        return stream().findAny().isEmpty();
    }

    public int size() {
        return Math.toIntExact(stream().count());
    }

    @Override
    public Iterator<T> iterator() {
        return stream().iterator();
    }
}
