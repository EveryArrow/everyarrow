/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.llm;

/**
 * Represents a request to the AI API.
 */
public class AIRequest {
    private final String model;
    private final String prompt;
    private final String systemPrompt;
    private final String userPrompt;
    private final String responseSchemaName;
    private final String responseJsonSchema;
    private final Double temperature;

    /**
     * Constructs an AIRequest with the specified model and prompt.
     *
     * @param model  The AI model to use.
     * @param prompt The prompt to send.
     */
    public AIRequest(String model, String prompt) {
        this(model, null, prompt, null, null, null);
    }

    /**
     * Constructs an AIRequest with separate system and user prompts.
     *
     * @param model        The AI model to use.
     * @param systemPrompt The system prompt to send.
     * @param userPrompt   The user prompt to send.
     */
    public AIRequest(String model, String systemPrompt, String userPrompt) {
        this(model, systemPrompt, userPrompt, null, null, null);
    }

    /**
     * Constructs an AIRequest with optional structured output settings.
     *
     * @param model              The AI model to use.
     * @param systemPrompt       The system prompt to send.
     * @param userPrompt         The user prompt to send.
     * @param responseSchemaName The name of the response schema.
     * @param responseJsonSchema The JSON schema for structured responses.
     * @param temperature        The sampling temperature.
     */
    public AIRequest(String model,
                     String systemPrompt,
                     String userPrompt,
                     String responseSchemaName,
                     String responseJsonSchema,
                     Double temperature) {
        this.model = blankToNull(model);
        this.systemPrompt = blankToNull(systemPrompt);
        this.userPrompt = blankToNull(userPrompt);
        this.prompt = this.userPrompt;
        this.responseSchemaName = blankToNull(responseSchemaName);
        this.responseJsonSchema = blankToNull(responseJsonSchema);
        this.temperature = temperature;
    }

    public String getModel() {
        return model;
    }

    /**
     * Legacy accessor retained for backward compatibility.
     * Acts as an alias for the user prompt.
     *
     * @return The user prompt content.
     */
    public String getPrompt() {
        return prompt;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String getUserPrompt() {
        return userPrompt;
    }

    public String getResponseSchemaName() {
        return responseSchemaName;
    }

    public String getResponseJsonSchema() {
        return responseJsonSchema;
    }

    public Double getTemperature() {
        return temperature;
    }

    public boolean hasSystemPrompt() {
        return systemPrompt != null;
    }

    public boolean hasStructuredResponseSchema() {
        return responseJsonSchema != null;
    }

    public static Builder builder(String model) {
        return new Builder(model);
    }

    private static String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value;
    }

    public static final class Builder {
        private final String model;
        private String systemPrompt;
        private String userPrompt;
        private String responseSchemaName;
        private String responseJsonSchema;
        private Double temperature;

        private Builder(String model) {
            this.model = model;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder userPrompt(String userPrompt) {
            this.userPrompt = userPrompt;
            return this;
        }

        public Builder responseSchemaName(String responseSchemaName) {
            this.responseSchemaName = responseSchemaName;
            return this;
        }

        public Builder responseJsonSchema(String responseJsonSchema) {
            this.responseJsonSchema = responseJsonSchema;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public AIRequest build() {
            return new AIRequest(model, systemPrompt, userPrompt, responseSchemaName, responseJsonSchema, temperature);
        }
    }
}
