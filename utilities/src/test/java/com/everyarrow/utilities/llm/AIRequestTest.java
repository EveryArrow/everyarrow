/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AIRequestTest {

    @Test
    void legacyConstructorPreservesExistingPromptBehavior() {
        AIRequest request = new AIRequest("gpt-4.1-mini", "Score this lead.");

        assertEquals("gpt-4.1-mini", request.getModel());
        assertEquals("Score this lead.", request.getPrompt());
        assertEquals("Score this lead.", request.getUserPrompt());
        assertNull(request.getSystemPrompt());
        assertNull(request.getResponseSchemaName());
        assertNull(request.getResponseJsonSchema());
        assertNull(request.getTemperature());
    }

    @Test
    void builderStoresSystemUserAndStructuredSettings() {
        AIRequest request = AIRequest.builder("gpt-4.1-mini")
                .systemPrompt("You are a scorer.")
                .userPrompt("Lead data goes here.")
                .responseSchemaName("lead_scores")
                .responseJsonSchema("{\"type\":\"object\"}")
                .temperature(0.2d)
                .build();

        assertEquals("You are a scorer.", request.getSystemPrompt());
        assertEquals("Lead data goes here.", request.getUserPrompt());
        assertEquals("Lead data goes here.", request.getPrompt());
        assertEquals("lead_scores", request.getResponseSchemaName());
        assertEquals("{\"type\":\"object\"}", request.getResponseJsonSchema());
        assertEquals(0.2d, request.getTemperature());
        assertTrue(request.hasSystemPrompt());
        assertTrue(request.hasStructuredResponseSchema());
    }
}
