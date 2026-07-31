/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.templates;

import com.everyarrow.utilities.llm.AIClientException;

/**
 * Interface for providing dynamic content through prompts.
 */
public interface PromptProvider {
    /**
     * Gets the response for the prompt.
     *
     * @return The prompt response as a String.
     */
    String getPromptResponse() throws AIClientException;
}
