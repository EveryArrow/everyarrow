/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.templates;

import com.everyarrow.utilities.llm.AIClientException;
import com.google.gson.JsonObject;

/**
 * Interface for providers that return structured JSON prompt responses.
 */
public interface StructuredPromptProvider {
    /**
     * Gets the structured response for the prompt.
     *
     * @return The prompt response as a JSON object.
     */
    JsonObject getStructuredPromptResponse() throws AIClientException;
}
