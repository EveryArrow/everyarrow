/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.llm;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.List;

/**
 * Abstract class representing an AI client for making API calls.
 */
public abstract class AIClient {
    protected String apiKey;

    /**
     * Constructs an AIClient with the specified API key and URL.
     *
     * @param apiKey The API key.
     * @param apiUrl The API URL.
     */
    public AIClient(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Calls the AI API with the given request.
     *
     * @param request The AIRequest object containing model and prompt.
     * @return The JSON response from the API.
     * @throws IOException If an I/O error occurs.
     */
    public abstract JsonObject callApi(AIRequest request) throws IOException, AIClientException;

    /**
     * Extracts content from the API response.
     *
     * @param response The JSON response from the API.
     * @return A list of content strings.
     */
    public abstract List<String> getContent(JsonObject response) throws AIClientException;
}
