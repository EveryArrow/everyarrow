/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.templates;

import com.everyarrow.utilities.templates.exceptions.TemplateProcessingException;

import java.util.List;
import java.util.Map;

final class TemplateRenderer {
    private final String templateText;
    private final Map<String, String> scalars;
    private final Map<String, List<String>> lists;
    private final Map<String, PromptProvider> prompts;

    TemplateRenderer(String templateText,
                     Map<String, String> scalars,
                     Map<String, List<String>> lists,
                     Map<String, PromptProvider> prompts) {
        this.templateText = templateText == null ? "" : templateText;
        this.scalars = scalars;
        this.lists = lists;
        this.prompts = prompts;
    }

    String resolve() throws TemplateProcessingException {
        StringBuilder result = new StringBuilder();
        int index = 0;
        while (index < templateText.length()) {
            char current = templateText.charAt(index);
            if (!isPlaceholderStart(index)) {
                result.append(current);
                index++;
                continue;
            }

            int start = index;
            index += 3;
            StringBuilder typeBuilder = new StringBuilder();
            while (index < templateText.length() && Character.isLetter(templateText.charAt(index))) {
                typeBuilder.append(templateText.charAt(index));
                index++;
            }

            if (index >= templateText.length() || templateText.charAt(index) != '(') {
                result.append(templateText, start, index);
                continue;
            }

            index++;
            StringBuilder parameters = new StringBuilder();
            int parenCount = 1;
            while (index < templateText.length() && parenCount > 0) {
                char character = templateText.charAt(index);
                if (character == '(') {
                    parenCount++;
                } else if (character == ')') {
                    parenCount--;
                }
                if (parenCount > 0) {
                    parameters.append(character);
                }
                index++;
            }
            result.append(processPlaceholder(typeBuilder.toString(), new TemplateParameters(parameters.toString())));
        }
        return result.toString();
    }

    private boolean isPlaceholderStart(int index) {
        return templateText.charAt(index) == '@'
                && index + 2 < templateText.length()
                && templateText.charAt(index + 1) == 'x'
                && templateText.charAt(index + 2) == ':';
    }

    private String processPlaceholder(String type, TemplateParameters parameters) throws TemplateProcessingException {
        return switch (type) {
            case "scalar" -> processScalar(parameters);
            case "list" -> processList(parameters);
            case "prompt" -> processPrompt(parameters);
            default -> throw new TemplateProcessingException("Unknown placeholder type: " + type);
        };
    }

    private String processScalar(TemplateParameters parameters) throws TemplateProcessingException {
        String variableName = parameters.value("text");
        if (variableName != null && scalars.containsKey(variableName)) {
            return scalars.get(variableName);
        }
        throw new TemplateProcessingException("Scalar variable not found: " + variableName);
    }

    private String processList(TemplateParameters parameters) throws TemplateProcessingException {
        String variableName = parameters.value("varName");
        String precedeText = parameters.value("precedeText");
        String postText = parameters.value("postText");
        String separator = parameters.value("separator", "\n");

        if (variableName == null || !lists.containsKey(variableName)) {
            throw new TemplateProcessingException("List variable not found: " + variableName);
        }

        List<String> items = lists.get(variableName);
        StringBuilder result = new StringBuilder();
        int count = 1;
        for (String item : items) {
            StringBuilder line = new StringBuilder();
            if (precedeText != null) {
                line.append(precedeText.replace("#cnt", String.valueOf(count)));
            }
            line.append(item);
            if (postText != null) {
                line.append(postText);
            }
            result.append(line);
            if (count < items.size()) {
                result.append(separator);
            }
            count++;
        }
        return result.toString();
    }

    private String processPrompt(TemplateParameters parameters) throws TemplateProcessingException {
        String variableName = parameters.value("varName");
        if (variableName == null || !prompts.containsKey(variableName)) {
            throw new TemplateProcessingException("Prompt variable not found: " + variableName);
        }

        PromptProvider provider = prompts.get(variableName);
        try {
            return provider.getPromptResponse();
        } catch (Exception e) {
            throw new TemplateProcessingException("Error processing prompt: " + variableName, e);
        }
    }
}
