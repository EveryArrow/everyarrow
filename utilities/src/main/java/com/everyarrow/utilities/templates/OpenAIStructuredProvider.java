/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.templates;

import com.everyarrow.utilities.llm.AIClientException;
import com.everyarrow.utilities.llm.AIRequest;
import com.everyarrow.utilities.llm.StructuredAIResultParser;
import com.everyarrow.utilities.llm.openai.OpenAIClient;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * Structured prompt provider backed by OpenAI and JSON parsing.
 */
public class OpenAIStructuredProvider implements StructuredPromptProvider {
    private final AIRequest request;
    private final OpenAIClient openAIClient;
    private final StructuredAIResultParser structuredAIResultParser;

    public OpenAIStructuredProvider(AIRequest request, OpenAIClient openAIClient) {
        this(request, openAIClient, new StructuredAIResultParser());
    }

    public OpenAIStructuredProvider(AIRequest request,
                                    OpenAIClient openAIClient,
                                    StructuredAIResultParser structuredAIResultParser) {
        this.request = request;
        this.openAIClient = openAIClient;
        this.structuredAIResultParser = structuredAIResultParser;
    }

    @Override
    public JsonObject getStructuredPromptResponse() throws AIClientException {
        JsonObject response = openAIClient.callApi(request);
        List<String> contents = openAIClient.getContent(response);

        if (contents.isEmpty()) {
            throw new AIClientException("No structured content received from OpenAI API.");
        }

        return structuredAIResultParser.parse(contents.getFirst(), request.getResponseJsonSchema());
    }
}
