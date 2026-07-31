/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.templates;

import com.everyarrow.utilities.templates.exceptions.TemplateProcessingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A class for processing templates with custom placeholders,
 * supporting scalar variables, lists, and prompts.
 * Utilizes the builder pattern for construction and custom parsing for variable substitution.
 *
 * Also provides a static utility method for retrieving the resolved template text
 * for a given namespace.
 */
public class TemplateProcessor {
    private final String templateText;
    private final Map<String, String> scalars;
    private final Map<String, List<String>> lists;
    private final Map<String, PromptProvider> prompts;

    private TemplateProcessor(Builder builder) {
        this.templateText = builder.templateText.toString();
        this.scalars = builder.scalars;
        this.lists = builder.lists;
        this.prompts = builder.prompts;
    }

    /**
     * Resolves the template by substituting all placeholders with their corresponding values.
     *
     * @return The resolved template as a String.
     * @throws TemplateProcessingException If an error occurs during template processing.
     */
    public String resolve() throws TemplateProcessingException {
        try {
            return new TemplateRenderer(templateText, scalars, lists, prompts).resolve();
        } catch (Exception e) {
            throw new TemplateProcessingException("Error processing template", e);
        }
    }

    /**
     * Builder class for constructing Template instances.
     */
    public static class Builder {
        private final StringBuilder templateText = new StringBuilder();
        private final Map<String, String> scalars = new HashMap<>();
        private final Map<String, List<String>> lists = new HashMap<>();
        private final Map<String, PromptProvider> prompts = new HashMap<>();

        /**
         * Adds text to the template.
         *
         * @param text The text to add.
         * @return The Builder instance.
         */
        public Builder addText(String text) {
            this.templateText.append(text);
            return this;
        }

        /**
         * Adds scalar variables for substitution.
         *
         * @param scalars A map of variable names and their values.
         * @return The Builder instance.
         */
        public Builder addScalars(Map<String, String> scalars) {
            this.scalars.putAll(scalars);
            return this;
        }

        /**
         * Adds a list variable for substitution.
         *
         * @param name   The name of the list variable.
         * @param values The list of values.
         * @return The Builder instance.
         */
        public Builder addList(String name, List<String> values) {
            this.lists.put(name, values);
            return this;
        }

        /**
         * Adds a prompt provider for dynamic content generation.
         *
         * @param name     The name of the prompt variable.
         * @param provider The PromptProvider instance.
         * @return The Builder instance.
         */
        public Builder addPrompt(String name, PromptProvider provider) {
            this.prompts.put(name, provider);
            return this;
        }

        /**
         * Builds and returns the Template instance.
         *
         * @return The constructed Template instance.
         */
        public TemplateProcessor build() {
            return new TemplateProcessor(this);
        }
    }

    /**
     * Static utility method to retrieve a resolved template from a namespaced Template class.
     * Assumes that for each namespace there is a Template class in the package
     * "com.everyarrow.utilities.templates.providers.<namespace>" that defines a public static getTemplate() method.
     *
     * @param namespace The namespace of an installed template provider (for example, "custom.notifications").
     * @return The resolved template text.
     * @throws TemplateProcessingException If there is an error loading or processing the template.
     */
    public static String getResolvedTemplate(String namespace) throws TemplateProcessingException {
        return TemplateNamespaceResolver.load(namespace).resolve();
    }
}
