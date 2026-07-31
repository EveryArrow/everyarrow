/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.json;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;

/**
 * Low-level JSON codec and compatibility facade for Gson-based utilities.
 */
public final class JsonUtils {

    private static final JsonSerializer<LocalDateTime> LDT_SERIALIZER = (src, typeOfSrc, ctx) ->
            new JsonPrimitive(src.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    private static final JsonDeserializer<LocalDateTime> LDT_DESERIALIZER = (json, typeOfT, ctx) ->
            LocalDateTime.parse(json.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    private static final JsonSerializer<ZonedDateTime> ZDT_SERIALIZER = (src, typeOfSrc, ctx) ->
            new JsonPrimitive(src.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    private static final JsonDeserializer<ZonedDateTime> ZDT_DESERIALIZER = (json, typeOfT, ctx) ->
            ZonedDateTime.parse(json.getAsString(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .registerTypeAdapter(LocalDateTime.class, LDT_SERIALIZER)
            .registerTypeAdapter(LocalDateTime.class, LDT_DESERIALIZER)
            .registerTypeAdapter(ZonedDateTime.class, ZDT_SERIALIZER)
            .registerTypeAdapter(ZonedDateTime.class, ZDT_DESERIALIZER)
            .create();

    private JsonUtils() {
    }

    /**
     * Returns the shared configured Gson instance used by this package.
     */
    public static Gson getGson() {
        return GSON;
    }

    /**
     * Parses object JSON leniently.
     * Returns an empty object for null, blank, invalid, or non-object JSON.
     */
    public static JsonObject parse(String json) {
        if (json == null || json.isBlank()) {
            return new JsonObject();
        }

        try {
            return parseStrict(json);
        } catch (IllegalArgumentException ex) {
            return new JsonObject();
        }
    }

    /**
     * Parses object JSON strictly.
     *
     * @throws IllegalArgumentException when the text is null, blank, invalid JSON, or not a JSON object
     */
    public static JsonObject parseStrict(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("JSON text must not be null or blank.");
        }

        try {
            JsonElement element = JsonParser.parseString(json);
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("JSON text must describe an object.");
            }
            return element.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException ex) {
            throw new IllegalArgumentException("Invalid JSON object.", ex);
        }
    }

    /**
     * Serializes an object using the shared configured Gson instance.
     * Returns an error object when serialization fails.
     */
    public static String toJson(Object obj) {
        return GSON.toJson(toJsonTree(obj));
    }

    /**
     * Converts an object into a JSON tree using the shared configured Gson instance.
     * Returns an error object when serialization fails.
     */
    public static JsonElement toJsonTree(Object obj) {
        try {
            return GSON.toJsonTree(obj);
        } catch (RuntimeException ex) {
            JsonObject fallback = new JsonObject();
            fallback.addProperty("error", "Failed to serialize object: " + ex.getMessage());
            return fallback;
        }
    }

    /**
     * Compatibility helper for shallow property presence checks.
     */
    public static boolean existsAndNotNull(JsonObject jsonObject, String property) {
        return jsonObject != null
                && property != null
                && jsonObject.has(property)
                && !jsonObject.get(property).isJsonNull();
    }

    /**
     * Compatibility helper for response envelopes.
     */
    public static String error(String message, Exception exception) {
        JsonObject error = new JsonObject();
        error.addProperty("success", false);
        error.addProperty("message", message);
        if (exception != null && exception.getMessage() != null) {
            error.addProperty("details", exception.getMessage());
        }
        return GSON.toJson(error);
    }

    /**
     * Compatibility helper for response envelopes.
     */
    public static String success(Object data) {
        JsonObject success = new JsonObject();
        success.addProperty("success", true);
        success.add("data", toJsonTree(data));
        return GSON.toJson(success);
    }
}
