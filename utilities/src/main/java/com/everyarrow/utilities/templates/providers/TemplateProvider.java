/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.templates.providers;

import com.everyarrow.utilities.templates.TemplateProcessor;
import com.everyarrow.utilities.templates.exceptions.TemplateProcessingException;
import com.everyarrow.utilities.templates.PromptProvider;

import java.util.List;
import java.util.Map;

public abstract class TemplateProvider {
    protected String templateText;
    protected Map<String, String> scalars;
    protected Map<String, List<String>> lists;
    protected Map<String, PromptProvider> prompts;

    /**
     * Constructor to initialize the template variables.
     *
     * @param templateText The raw template text.
     * @param scalars      A map of scalar variable substitutions.
     * @param lists        A map of list variable substitutions.
     * @param prompts      A map of prompt variable substitutions.
     */
    public TemplateProvider(String templateText,
                            Map<String, String> scalars,
                            Map<String, List<String>> lists,
                            Map<String, PromptProvider> prompts) {
        this.templateText = templateText;
        this.scalars = scalars;
        this.lists = lists;
        this.prompts = prompts;
    }

    /**
     * Returns a fully built Template instance.
     *
     * @return the Template instance.
     * @throws TemplateProcessingException if an error occurs during template creation.
     */
    public abstract TemplateProcessor getTemplate() throws TemplateProcessingException;

    /**
     * Returns the raw template text used by this provider.
     *
     * @return the raw template text.
     */
    public String getTemplateText() {
        return templateText;
    }

    /**
     * Returns the resolved (output) template text.
     *
     * @return the resolved template text.
     * @throws TemplateProcessingException if an error occurs during template processing.
     */
    public String getResolvedTemplateText() throws TemplateProcessingException {
        return getTemplate().resolve();
    }
}
