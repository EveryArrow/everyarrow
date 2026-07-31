/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.llm.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.everyarrow.utilities.llm.AIClientException;
import com.everyarrow.utilities.llm.AIRequest;
import com.openai.models.ResponseFormatJsonSchema;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import org.junit.jupiter.api.Test;

class OpenAIClientTest {

    private static final String TEST_SCHEMA_NAME = "test_result";
    private static final String TEST_JSON_SCHEMA = """
            {
              "type": "object",
              "additionalProperties": false,
              "required": ["value"],
              "properties": {
                "value": { "type": "string" }
              }
            }
            """;

    private final OpenAIClient client = new OpenAIClient("test-key");

    @Test
    void buildChatCompletionCreateParamsKeepsLegacyPromptAsSingleUserMessage() throws AIClientException {
        AIRequest request = new AIRequest("gpt-4.1-mini", "Legacy prompt content.");

        ChatCompletionCreateParams params = client.buildChatCompletionCreateParams(request);

        assertEquals("gpt-4.1-mini", params.model().asString());
        assertEquals(1, params.messages().size());
        assertTrue(params.messages().get(0).isUser());
        assertEquals("Legacy prompt content.", params.messages().get(0).asUser().content().asText());
        assertTrue(params.temperature().isEmpty());
        assertTrue(params.responseFormat().isEmpty());
    }

    @Test
    void buildChatCompletionCreateParamsAddsSystemUserTemperatureAndSchema() throws AIClientException {
        AIRequest request = AIRequest.builder("gpt-4.1-mini")
                .systemPrompt("Use the EveryArrow rubric.")
                .userPrompt("Lead 1: Jane Smith | VP Growth | Acme")
                .responseSchemaName(TEST_SCHEMA_NAME)
                .responseJsonSchema(TEST_JSON_SCHEMA)
                .temperature(0.2d)
                .build();

        ChatCompletionCreateParams params = client.buildChatCompletionCreateParams(request);
        ChatCompletionMessageParam systemMessage = params.messages().get(0);
        ChatCompletionMessageParam userMessage = params.messages().get(1);
        ResponseFormatJsonSchema responseFormat = params.responseFormat().orElseThrow().asJsonSchema();

        assertEquals(2, params.messages().size());
        assertTrue(systemMessage.isSystem());
        assertEquals("Use the EveryArrow rubric.", systemMessage.asSystem().content().asText());
        assertTrue(userMessage.isUser());
        assertEquals("Lead 1: Jane Smith | VP Growth | Acme", userMessage.asUser().content().asText());
        assertEquals(0.2d, params.temperature().orElseThrow());
        assertFalse(params.responseFormat().isEmpty());
        assertTrue(params.responseFormat().orElseThrow().isJsonSchema());
        assertEquals(TEST_SCHEMA_NAME, responseFormat.jsonSchema().name());
        assertTrue(responseFormat.jsonSchema().strict().orElseThrow());
        assertTrue(responseFormat.jsonSchema().schema().isPresent());
    }
}
