/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.templates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.everyarrow.utilities.templates.exceptions.TemplateProcessingException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TemplateProcessorTest {

    @Test
    void resolveStillHandlesScalarAndListTemplates() throws TemplateProcessingException {
        String resolved = new TemplateProcessor.Builder()
                .addText("""
                        Hello @x:scalar(text=name)
                        @x:list(varName=items,precedeText="- ",separator="\\n")
                        """)
                .addScalars(Map.of("name", "EveryArrow"))
                .addList("items", List.of("Score leads", "Route outreach"))
                .build()
                .resolve();

        assertEquals("""
                Hello EveryArrow
                - Score leads
                - Route outreach
                """.trim(), resolved.trim());
    }

    @Test
    void getResolvedTemplateReportsMissingNamespaceClearly() {
        TemplateProcessingException exception = assertThrows(
                TemplateProcessingException.class,
                () -> TemplateProcessor.getResolvedTemplate("missing.namespace"));

        org.junit.jupiter.api.Assertions.assertTrue(
                exception.getMessage().contains("Template namespace not found: missing.namespace"));
    }
}
