/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.templates.providers.postgres.procedures.delete;

import com.everyarrow.utilities.templates.PromptProvider;
import com.everyarrow.utilities.templates.TemplateProcessor;
import com.everyarrow.utilities.templates.exceptions.TemplateProcessingException;
import com.everyarrow.utilities.templates.providers.TemplateProvider;
import com.everyarrow.utilities.db.MetadataUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeleteProvider extends TemplateProvider {

    public DeleteProvider(String templateText, PromptProvider promptProvider,
                          Connection conn, String schema, String tableName) throws TemplateProcessingException {
        super(templateText, generateScalars(conn, schema, tableName), getDefaultLists(), getDefaultPrompts(promptProvider));
    }

    public DeleteProvider(String templateText,
                          Connection conn, String schema, String tableName) throws TemplateProcessingException {
        this(templateText, null, conn, schema, tableName);
    }

    private static Map<String, String> generateScalars(Connection conn, String schema, String tableName) throws TemplateProcessingException {
        try {
            MetadataUtils.TableMetadata metadata = MetadataUtils.getTableMetadata(conn, schema, tableName);
            List<String> pkColumns = metadata.getPrimaryKeys();
            if (pkColumns.isEmpty()) {
                throw new TemplateProcessingException("No primary key found for table " + tableName);
            }
            String pk = pkColumns.get(0);
            return DeleteTemplateParameterGenerator.generateSubstitutions(tableName, metadata.getAllColumns(), pk, metadata.getColumnTypes());
        } catch (SQLException e) {
            throw new TemplateProcessingException("Error retrieving metadata", e);
        }
    }

    private static Map<String, List<String>> getDefaultLists() {
        return new HashMap<>();
    }

    private static Map<String, PromptProvider> getDefaultPrompts(PromptProvider promptProvider) {
        Map<String, PromptProvider> prompts = new HashMap<>();
        if (promptProvider != null) {
            prompts.put("dynamicContent", promptProvider);
        }
        return prompts;
    }

    @Override
    public TemplateProcessor getTemplate() throws TemplateProcessingException {
        TemplateProcessor.Builder builder = new TemplateProcessor.Builder();
        builder.addText(templateText);
        builder.addScalars(scalars);
        for (Map.Entry<String, List<String>> entry : lists.entrySet()) {
            builder.addList(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, PromptProvider> entry : prompts.entrySet()) {
            builder.addPrompt(entry.getKey(), entry.getValue());
        }
        return builder.build();
    }
}
