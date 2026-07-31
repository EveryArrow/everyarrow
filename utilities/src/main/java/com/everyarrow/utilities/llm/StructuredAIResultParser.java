/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.llm;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;

/**
 * Parses structured LLM output and validates it against a JSON schema subset.
 */
public class StructuredAIResultParser {
    private final Gson gson;

    public StructuredAIResultParser() {
        this(new Gson());
    }

    public StructuredAIResultParser(Gson gson) {
        this.gson = gson;
    }

    public JsonObject parse(String content) throws AIClientException {
        return parse(content, null);
    }

    public JsonObject parse(String content, String responseJsonSchema) throws AIClientException {
        if (content == null || content.trim().isEmpty()) {
            throw new AIClientException("Structured response content cannot be null or empty.");
        }

        JsonElement responseElement = parseJson(normalizeJsonContent(content), "structured response");
        if (!responseElement.isJsonObject()) {
            throw new AIClientException("Structured response must be a JSON object.");
        }

        if (responseJsonSchema != null && !responseJsonSchema.isBlank()) {
            JsonElement schemaElement = parseJson(responseJsonSchema, "response JSON schema");
            if (!schemaElement.isJsonObject()) {
                throw new AIClientException("Response JSON schema must be a JSON object.");
            }
            validateAgainstSchema(responseElement, schemaElement.getAsJsonObject(), "$");
        }

        return responseElement.getAsJsonObject();
    }

    public Gson getGson() {
        return gson;
    }

    private JsonElement parseJson(String json, String label) throws AIClientException {
        try {
            return JsonParser.parseString(json);
        } catch (JsonSyntaxException ex) {
            throw new AIClientException("Unable to parse " + label + " as JSON.", ex);
        }
    }

    private String normalizeJsonContent(String content) {
        String normalized = content.trim();

        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```(?:json)?\\s*", "");
            normalized = normalized.replaceFirst("\\s*```$", "");
        }

        if (!normalized.startsWith("{")) {
            int objectStart = normalized.indexOf('{');
            int objectEnd = normalized.lastIndexOf('}');
            if (objectStart >= 0 && objectEnd > objectStart) {
                normalized = normalized.substring(objectStart, objectEnd + 1);
            }
        }

        return normalized.trim();
    }

    private void validateAgainstSchema(JsonElement value, JsonObject schema, String path) throws AIClientException {
        validateType(value, schema, path);
        validateEnum(value, schema, path);
        validateNumericBounds(value, schema, path);

        String type = getType(schema);
        if ("object".equals(type) && value.isJsonObject()) {
            validateObject(value.getAsJsonObject(), schema, path);
        } else if ("array".equals(type) && value.isJsonArray()) {
            validateArray(value.getAsJsonArray(), schema, path);
        }
    }

    private void validateType(JsonElement value, JsonObject schema, String path) throws AIClientException {
        if (!schema.has("type")) {
            return;
        }

        JsonElement typeElement = schema.get("type");
        if (typeElement.isJsonPrimitive()) {
            String type = typeElement.getAsString();
            if (!matchesType(value, type)) {
                throw new AIClientException("JSON value at " + path + " must be of type " + type + ".");
            }
            return;
        }

        if (typeElement.isJsonArray()) {
            JsonArray types = typeElement.getAsJsonArray();
            for (JsonElement candidate : types) {
                if (candidate.isJsonPrimitive() && matchesType(value, candidate.getAsString())) {
                    return;
                }
            }
            throw new AIClientException("JSON value at " + path + " does not match any allowed schema type.");
        }
    }

    private boolean matchesType(JsonElement value, String type) {
        return switch (type) {
            case "object" -> value.isJsonObject();
            case "array" -> value.isJsonArray();
            case "string" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
            case "number" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber();
            case "integer" -> isInteger(value);
            case "boolean" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean();
            case "null" -> value.isJsonNull();
            default -> true;
        };
    }

    private boolean isInteger(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return false;
        }

        double numericValue = value.getAsDouble();
        return Math.floor(numericValue) == numericValue;
    }

    private void validateEnum(JsonElement value, JsonObject schema, String path) throws AIClientException {
        if (!schema.has("enum")) {
            return;
        }

        JsonArray allowedValues = schema.getAsJsonArray("enum");
        for (JsonElement allowedValue : allowedValues) {
            if (allowedValue.equals(value)) {
                return;
            }
        }

        throw new AIClientException("JSON value at " + path + " does not match the allowed enum values.");
    }

    private void validateNumericBounds(JsonElement value, JsonObject schema, String path) throws AIClientException {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return;
        }

        double numericValue = value.getAsDouble();
        if (schema.has("minimum") && numericValue < schema.get("minimum").getAsDouble()) {
            throw new AIClientException("JSON value at " + path + " is below the schema minimum.");
        }
        if (schema.has("maximum") && numericValue > schema.get("maximum").getAsDouble()) {
            throw new AIClientException("JSON value at " + path + " is above the schema maximum.");
        }
    }

    private void validateObject(JsonObject object, JsonObject schema, String path) throws AIClientException {
        JsonObject properties = schema.has("properties") && schema.get("properties").isJsonObject()
                ? schema.getAsJsonObject("properties")
                : new JsonObject();

        if (schema.has("required") && schema.get("required").isJsonArray()) {
            for (JsonElement requiredElement : schema.getAsJsonArray("required")) {
                String requiredProperty = requiredElement.getAsString();
                if (!object.has(requiredProperty)) {
                    throw new AIClientException("Missing required JSON property at " + path + ": "
                            + requiredProperty + ".");
                }
            }
        }

        for (String propertyName : properties.keySet()) {
            if (object.has(propertyName)) {
                JsonElement propertySchema = properties.get(propertyName);
                if (propertySchema.isJsonObject()) {
                    validateAgainstSchema(object.get(propertyName), propertySchema.getAsJsonObject(),
                            path + "." + propertyName);
                }
            }
        }

        if (schema.has("additionalProperties")
                && schema.get("additionalProperties").isJsonPrimitive()
                && schema.getAsJsonPrimitive("additionalProperties").isBoolean()
                && !schema.get("additionalProperties").getAsBoolean()) {
            for (String propertyName : object.keySet()) {
                if (!properties.has(propertyName)) {
                    throw new AIClientException("Unexpected JSON property at " + path + ": "
                            + propertyName + ".");
                }
            }
        }
    }

    private void validateArray(JsonArray array, JsonObject schema, String path) throws AIClientException {
        if (!schema.has("items") || !schema.get("items").isJsonObject()) {
            return;
        }

        JsonObject itemsSchema = schema.getAsJsonObject("items");
        for (int i = 0; i < array.size(); i++) {
            validateAgainstSchema(array.get(i), itemsSchema, path + "[" + i + "]");
        }
    }

    private String getType(JsonObject schema) {
        if (!schema.has("type")) {
            return null;
        }

        JsonElement typeElement = schema.get("type");
        if (typeElement.isJsonPrimitive()) {
            JsonPrimitive primitive = typeElement.getAsJsonPrimitive();
            if (primitive.isString()) {
                return primitive.getAsString();
            }
        }

        return null;
    }
}
