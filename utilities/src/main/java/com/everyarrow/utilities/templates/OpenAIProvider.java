/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.templates;

import com.everyarrow.utilities.llm.AIRequest;
import com.everyarrow.utilities.llm.openai.OpenAIClient;
import com.everyarrow.utilities.llm.AIClientException;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * An implementation of PromptProvider using OpenAI.
 */
public class OpenAIProvider implements PromptProvider {
    private final String prompt;
    private final OpenAIClient openAIClient;
    private final String model;

    /**
     * Constructs an OpenAIProvider with the specified prompt, OpenAIClient, and model.
     *
     * @param prompt       The prompt to send to the OpenAI API.
     * @param openAIClient The OpenAIClient instance to use for API calls.
     * @param model        The OpenAI model to use (e.g., "gpt-3.5-turbo").
     */
    public OpenAIProvider(String prompt, OpenAIClient openAIClient, String model) {
        this.prompt = prompt;
        this.openAIClient = openAIClient;
        this.model = model;
    }

    @Override
    public String getPromptResponse() throws AIClientException {
        AIRequest aiRequest = new AIRequest(model, prompt);
        JsonObject response = openAIClient.callApi(aiRequest);
        List<String> contents = openAIClient.getContent(response);
        if (!contents.isEmpty()) {
            return contents.getFirst();
        } else {
            throw new AIClientException("No content received from OpenAI API.");
        }
    }
}
