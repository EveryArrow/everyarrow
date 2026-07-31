/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.templates;

import com.everyarrow.utilities.templates.exceptions.TemplateProcessingException;

import java.lang.reflect.Method;

final class TemplateNamespaceResolver {
    private static final String TEMPLATE_PACKAGE = "com.everyarrow.utilities.templates.providers.";

    private TemplateNamespaceResolver() {
    }

    static TemplateProcessor load(String namespace) throws TemplateProcessingException {
        if (namespace == null || namespace.isBlank()) {
            throw new TemplateProcessingException("Template namespace is required.");
        }

        String templateClassName = TEMPLATE_PACKAGE + namespace.trim() + ".Template";
        try {
            Class<?> clazz = Class.forName(templateClassName);
            Method getTemplateMethod = clazz.getMethod("getTemplate");
            Object templateObject = getTemplateMethod.invoke(null);
            if (templateObject instanceof TemplateProcessor template) {
                return template;
            }
            throw new TemplateProcessingException(
                    "Template namespace did not return a TemplateProcessor: " + namespace);
        } catch (ClassNotFoundException e) {
            throw new TemplateProcessingException("Template namespace not found: " + namespace, e);
        } catch (TemplateProcessingException e) {
            throw e;
        } catch (Exception e) {
            throw new TemplateProcessingException("Error loading template for namespace: " + namespace, e);
        }
    }
}
