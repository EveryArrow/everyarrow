/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.llm.openai;

import com.everyarrow.utilities.llm.AIClient;
import com.everyarrow.utilities.llm.AIClientException;
import com.everyarrow.utilities.llm.AIRequest;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.openai.core.JsonValue;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ResponseFormatJsonSchema;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Implementation of AIClient for OpenAI API using the official OpenAI Java SDK.
 */
public class OpenAIClient extends AIClient {
    private static final String DEFAULT_RESPONSE_SCHEMA_NAME = "structured_response";

    private final com.openai.client.OpenAIClient openAIClient;
    private final Gson gson;

    /**
     * Constructs an OpenAIClient with the specified API key.
     *
     * @param apiKey The OpenAI API key.
     */
    public OpenAIClient(String apiKey) {
        super(apiKey);
        this.openAIClient = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
        this.gson = new Gson();
    }

    @Override
    public JsonObject callApi(AIRequest request) throws AIClientException {
        try {
            ChatCompletionCreateParams params = buildChatCompletionCreateParams(request);

            ChatCompletion chatCompletion = openAIClient.chat().completions().create(params);
            chatCompletion.usage().ifPresent(usage -> System.out.println("Tokens used: " + usage.totalTokens()));

            JsonObject responseJson = new JsonObject();
            JsonArray choicesArray = new JsonArray();

            for (ChatCompletion.Choice choice : chatCompletion.choices()) {
                JsonObject choiceJson = new JsonObject();
                JsonObject messageJson = new JsonObject();

                choice.message().content().ifPresent(c -> messageJson.addProperty("content", c));
                choiceJson.add("message", messageJson);
                choiceJson.addProperty("index", choice.index());

                choicesArray.add(choiceJson);
            }

            responseJson.add("choices", choicesArray);
            return responseJson;
        } catch (Exception e) {
            throw new AIClientException("Error communicating with OpenAI API.", e);
        }
    }

    ChatCompletionCreateParams buildChatCompletionCreateParams(AIRequest request) throws AIClientException {
        validateRequest(request);

        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                .model(request.getModel());

        if (request.hasSystemPrompt()) {
            builder.addSystemMessage(request.getSystemPrompt());
        }

        builder.addUserMessage(request.getUserPrompt());

        if (request.getTemperature() != null) {
            builder.temperature(request.getTemperature());
        }

        if (request.hasStructuredResponseSchema()) {
            builder.responseFormat(buildResponseFormatJsonSchema(request));
        }

        return builder.build();
    }

    @Override
    public List<String> getContent(JsonObject response) throws AIClientException {
        List<String> contents = new ArrayList<>();

        if (response.has("choices")) {
            JsonArray choices = response.getAsJsonArray("choices");
            for (JsonElement choiceElement : choices) {
                JsonObject choice = choiceElement.getAsJsonObject();
                if (choice.has("message")) {
                    JsonObject message = choice.getAsJsonObject("message");
                    if (message.has("content")) {
                        contents.add(message.get("content").getAsString());
                    } else {
                        throw new AIClientException("Content not found in message.");
                    }
                } else {
                    throw new AIClientException("Message not found in choice.");
                }
            }
        } else {
            throw new AIClientException("Choices not found in response.");
        }

        return contents;
    }

    private void validateRequest(AIRequest request) throws AIClientException {
        if (request == null) {
            throw new AIClientException("AI request cannot be null.");
        }
        if (request.getModel() == null || request.getModel().trim().isEmpty()) {
            throw new AIClientException("Model cannot be null or empty.");
        }
        if (request.getUserPrompt() == null || request.getUserPrompt().trim().isEmpty()) {
            throw new AIClientException("User prompt cannot be null or empty.");
        }
    }

    private ResponseFormatJsonSchema buildResponseFormatJsonSchema(AIRequest request) throws AIClientException {
        JsonElement schemaElement;
        try {
            schemaElement = JsonParser.parseString(request.getResponseJsonSchema());
        } catch (Exception ex) {
            throw new AIClientException("Response JSON schema must be valid JSON.", ex);
        }

        if (!schemaElement.isJsonObject()) {
            throw new AIClientException("Response JSON schema must be a JSON object.");
        }

        ResponseFormatJsonSchema.JsonSchema.Schema.Builder schemaBuilder =
                ResponseFormatJsonSchema.JsonSchema.Schema.builder();

        for (Map.Entry<String, JsonElement> entry : schemaElement.getAsJsonObject().entrySet()) {
            schemaBuilder.putAdditionalProperty(entry.getKey(), toOpenAIJsonValue(entry.getValue()));
        }

        ResponseFormatJsonSchema.JsonSchema jsonSchema = ResponseFormatJsonSchema.JsonSchema.builder()
                .name(resolveResponseSchemaName(request))
                .schema(schemaBuilder.build())
                .strict(true)
                .build();

        return ResponseFormatJsonSchema.builder()
                .jsonSchema(jsonSchema)
                .build();
    }

    private String resolveResponseSchemaName(AIRequest request) {
        if (request.getResponseSchemaName() == null || request.getResponseSchemaName().trim().isEmpty()) {
            return DEFAULT_RESPONSE_SCHEMA_NAME;
        }
        return request.getResponseSchemaName();
    }

    private JsonValue toOpenAIJsonValue(JsonElement element) {
        return JsonValue.from(gson.fromJson(element, Object.class));
    }
}
