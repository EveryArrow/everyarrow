/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.templates.providers.ses;

import com.everyarrow.utilities.templates.TemplateProcessor;
import com.everyarrow.utilities.templates.PromptProvider;
import com.everyarrow.utilities.templates.exceptions.TemplateProcessingException;

import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SES Email Template Provider with spin-tax, personalization, and optional LLM support.
 * 
 * Features:
 * - Spin-tax: {option1|option2|option3} - randomly selects one option
 * - Personalization: {{variable_name}} - substitutes with provided values
 * - LLM Prompts: @x:prompt(varName=...) - integrates with PromptProvider for AI content
 * 
 * Usage:
 * <pre>
 * SesTemplateProvider provider = new SesTemplateProvider.Builder()
 *     .setSubjectTemplate("Hey {{first_name}}, {quick question|just reaching out}")
 *     .setBodyTemplate("Hi {{first_name}},\n\nI noticed you work at {{company_name}}...")
 *     .addVariable("first_name", "John")
 *     .addVariable("company_name", "Acme Inc")
 *     .addPrompt("personalized_opener", openAiProvider)
 *     .build();
 * 
 * String subject = provider.resolveSubject();
 * String body = provider.resolveBody();
 * </pre>
 */
public class SesTemplateProvider {
    
    // Spin-tax pattern: {opt1|opt2|opt3} - must contain at least one pipe
    private static final Pattern SPIN_TAX_PATTERN = Pattern.compile("\\{([^{}]+\\|[^{}]+)\\}");
    
    // Personalization pattern: {{variable_name}}
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");
    
    private final String subjectTemplate;
    private final String bodyTemplate;
    private final Map<String, String> variables;
    private final Map<String, PromptProvider> prompts;
    private final Random random;
    
    private SesTemplateProvider(Builder builder) {
        this.subjectTemplate = builder.subjectTemplate;
        this.bodyTemplate = builder.bodyTemplate;
        this.variables = builder.variables;
        this.prompts = builder.prompts;
        this.random = new Random();
    }
    
    /**
     * Resolves the subject template.
     * 
     * @return Fully resolved subject line
     * @throws TemplateProcessingException if processing fails
     */
    public String resolveSubject() throws TemplateProcessingException {
        return processTemplate(subjectTemplate);
    }
    
    /**
     * Resolves the body template.
     * 
     * @return Fully resolved email body
     * @throws TemplateProcessingException if processing fails
     */
    public String resolveBody() throws TemplateProcessingException {
        return processTemplate(bodyTemplate);
    }
    
    /**
     * Process a template string with all transformations.
     * Order: 1) Spin-tax 2) Variables 3) LLM prompts (via TemplateProcessor)
     */
    private String processTemplate(String template) throws TemplateProcessingException {
        if (template == null) return null;
        
        // Step 1: Apply spin-tax randomization
        String result = applySpinTax(template);
        
        // Step 2: Substitute personalization variables
        result = substituteVariables(result);
        
        // Step 3: If there are LLM prompts, use TemplateProcessor
        if (prompts != null && !prompts.isEmpty() && result.contains("@x:prompt")) {
            TemplateProcessor.Builder tpBuilder = new TemplateProcessor.Builder()
                .addText(result)
                .addScalars(variables);
            
            for (Map.Entry<String, PromptProvider> entry : prompts.entrySet()) {
                tpBuilder.addPrompt(entry.getKey(), entry.getValue());
            }
            
            result = tpBuilder.build().resolve();
        }
        
        return result;
    }
    
    /**
     * Apply spin-tax randomization: {opt1|opt2|opt3} -> randomly selected option
     */
    private String applySpinTax(String text) {
        if (text == null) return null;
        
        StringBuffer result = new StringBuffer();
        Matcher matcher = SPIN_TAX_PATTERN.matcher(text);
        
        while (matcher.find()) {
            String options = matcher.group(1);
            String[] parts = options.split("\\|");
            String selected = parts[random.nextInt(parts.length)].trim();
            matcher.appendReplacement(result, Matcher.quoteReplacement(selected));
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * Substitute personalization variables: {{var}} -> value
     */
    private String substituteVariables(String text) {
        if (text == null || variables == null) return text;
        
        StringBuffer result = new StringBuffer();
        Matcher matcher = VARIABLE_PATTERN.matcher(text);
        
        while (matcher.find()) {
            String varName = matcher.group(1).trim();
            String value = variables.getOrDefault(varName, "{{" + varName + "}}");
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * Builder for SesTemplateProvider.
     */
    public static class Builder {
        private String subjectTemplate;
        private String bodyTemplate;
        private final java.util.HashMap<String, String> variables = new java.util.HashMap<>();
        private final java.util.HashMap<String, PromptProvider> prompts = new java.util.HashMap<>();
        
        public Builder setSubjectTemplate(String subject) {
            this.subjectTemplate = subject;
            return this;
        }
        
        public Builder setBodyTemplate(String body) {
            this.bodyTemplate = body;
            return this;
        }
        
        public Builder addVariable(String name, String value) {
            if (value != null) {
                this.variables.put(name, value);
            }
            return this;
        }
        
        public Builder addVariables(Map<String, String> vars) {
            if (vars != null) {
                this.variables.putAll(vars);
            }
            return this;
        }
        
        public Builder addPrompt(String name, PromptProvider provider) {
            this.prompts.put(name, provider);
            return this;
        }
        
        public SesTemplateProvider build() {
            return new SesTemplateProvider(this);
        }
    }
}
