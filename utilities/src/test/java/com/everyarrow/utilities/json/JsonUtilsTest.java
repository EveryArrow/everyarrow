/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

class JsonUtilsTest {

    @Test
    void parseReturnsEmptyObjectForNullInput() {
        JsonObject parsed = JsonUtils.parse(null);

        assertTrue(parsed.entrySet().isEmpty());
    }

    @Test
    void parseReturnsEmptyObjectForBlankInput() {
        JsonObject parsed = JsonUtils.parse("   ");

        assertTrue(parsed.entrySet().isEmpty());
    }

    @Test
    void parseReturnsObjectForValidObjectJson() {
        JsonObject parsed = JsonUtils.parse("{\"name\":\"EveryArrow\"}");

        assertEquals("EveryArrow", parsed.get("name").getAsString());
    }

    @Test
    void parseReturnsEmptyObjectForNonObjectJson() {
        JsonObject parsed = JsonUtils.parse("[1,2,3]");

        assertTrue(parsed.entrySet().isEmpty());
    }

    @Test
    void parseStrictReturnsObjectForValidObjectJson() {
        JsonObject parsed = JsonUtils.parseStrict("{\"active\":true}");

        assertTrue(parsed.get("active").getAsBoolean());
    }

    @Test
    void parseStrictThrowsForInvalidJson() {
        assertThrows(IllegalArgumentException.class, () -> JsonUtils.parseStrict("{"));
    }

    @Test
    void getGsonReturnsConfiguredSharedInstance() {
        Gson gson = JsonUtils.getGson();

        assertSame(gson, JsonUtils.getGson());
    }

    @Test
    void toJsonUsesConfiguredGson() {
        TimestampHolder holder = new TimestampHolder(LocalDateTime.of(2026, 4, 19, 11, 12, 13));

        assertEquals(JsonUtils.getGson().toJson(holder), JsonUtils.toJson(holder));
    }

    @Test
    void localDateTimeSerializationUsesConfiguredAdapter() {
        TimestampHolder holder = new TimestampHolder(LocalDateTime.of(2026, 4, 19, 11, 12, 13));
        String json = JsonUtils.toJson(holder);

        assertTrue(json.contains("\"value\":\"2026-04-19T11:12:13\""));
    }

    @Test
    void zonedDateTimeSerializationUsesConfiguredAdapter() {
        ZonedTimestampHolder holder = new ZonedTimestampHolder(ZonedDateTime.of(
                2026, 4, 19, 11, 12, 13, 0, ZoneId.of("America/New_York")));
        String json = JsonUtils.toJson(holder);

        assertTrue(json.contains("\"value\":\"2026-04-19T11:12:13-04:00\""));
    }

    @Test
    void configuredGsonDeserializesLocalDateTime() {
        TimestampHolder holder = JsonUtils.getGson().fromJson(
                "{\"value\":\"2026-04-19T11:12:13\"}", TimestampHolder.class);

        assertEquals(LocalDateTime.of(2026, 4, 19, 11, 12, 13), holder.value());
    }

    @Test
    void configuredGsonDeserializesZonedDateTime() {
        ZonedTimestampHolder holder = JsonUtils.getGson().fromJson(
                "{\"value\":\"2026-04-19T11:12:13-04:00\"}", ZonedTimestampHolder.class);

        assertEquals(ZonedDateTime.parse("2026-04-19T11:12:13-04:00"), holder.value());
    }

    @Test
    void toJsonTreeUsesConfiguredGson() {
        JsonElement tree = JsonUtils.toJsonTree(new TimestampHolder(LocalDateTime.of(2026, 4, 19, 11, 12, 13)));

        assertEquals("2026-04-19T11:12:13", tree.getAsJsonObject().get("value").getAsString());
    }

    @Test
    void existsAndNotNullReturnsExpectedValues() {
        JsonObject object = JsonUtils.parseStrict("{\"present\":\"value\",\"empty\":null}");

        assertTrue(JsonUtils.existsAndNotNull(object, "present"));
        assertFalse(JsonUtils.existsAndNotNull(object, "missing"));
        assertFalse(JsonUtils.existsAndNotNull(object, "empty"));
    }

    @Test
    void successWrapsSerializedData() {
        JsonObject response = JsonUtils.parseStrict(JsonUtils.success(new TimestampHolder(
                LocalDateTime.of(2026, 4, 19, 11, 12, 13))));

        assertTrue(response.get("success").getAsBoolean());
        assertEquals("2026-04-19T11:12:13",
                response.getAsJsonObject("data").get("value").getAsString());
    }

    @Test
    void errorBuildsCompatibilityEnvelope() {
        JsonObject response = JsonUtils.parseStrict(
                JsonUtils.error("Unable to serialize", new IllegalStateException("boom")));

        assertFalse(response.get("success").getAsBoolean());
        assertEquals("Unable to serialize", response.get("message").getAsString());
        assertEquals("boom", response.get("details").getAsString());
    }

    private record TimestampHolder(LocalDateTime value) {
    }

    private record ZonedTimestampHolder(ZonedDateTime value) {
    }
}
