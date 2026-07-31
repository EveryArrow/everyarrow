/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.templates.providers.postgres.procedures.select;

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
import java.util.StringJoiner;

public class MultiSelectProvider extends TemplateProvider {

    private String fkCategory;

    /**
     * fkCategory should be "customer", "application", or "other".
     */
    public MultiSelectProvider(PromptProvider promptProvider,
                               Connection conn, String schema, String tableName, List<String> excludeColumns, String fkCategory)
            throws TemplateProcessingException {
        super(generateCombinedTemplate(conn, schema, tableName, excludeColumns, fkCategory),
                generateScalars(conn, schema, tableName, excludeColumns, fkCategory),
                getDefaultLists(), getDefaultPrompts(promptProvider));
        this.fkCategory = fkCategory;
    }

    public MultiSelectProvider(Connection conn, String schema, String tableName, List<String> excludeColumns, String fkCategory)
            throws TemplateProcessingException {
        this(null, conn, schema, tableName, excludeColumns, fkCategory);
    }

    private static String generateCombinedTemplate(Connection conn, String schema, String tableName, List<String> excludeColumns, String fkCategory)
            throws TemplateProcessingException {
        try {
            MetadataUtils.TableMetadata metadata = MetadataUtils.getTableMetadata(conn, schema, tableName);
            String baseTemplate;
            switch (fkCategory.toLowerCase()) {
                case "customer":
                    baseTemplate = TemplateSelectByCustomer.getTemplate();
                    break;
                case "application":
                    baseTemplate = TemplateSelectByApplication.getTemplate();
                    break;
                default:
                    baseTemplate = TemplateSelectByOtherFK.getTemplate();
                    break;
            }
            StringJoiner joiner = new StringJoiner(", ");
            for (String col : metadata.getAllColumns()) {
                if (excludeColumns != null && excludeColumns.contains(col)) continue;
                joiner.add(PostgresSelectIdentifiers.quoteIdentifier(col));
            }
            String selectColumns = joiner.toString();
            Map<String, String> subs = new HashMap<>();
            subs.put(
                    "TABLE_IDENTIFIER",
                    PostgresSelectIdentifiers.quoteIdentifier(tableName));
            subs.put("COLUMN_NAMES", selectColumns);
            if (fkCategory.equalsIgnoreCase("customer")) {
                String type = metadata.getColumnTypes().get("customer_id");
                requireType("customer_id", type);
                subs.put("CUSTOMER_KEY_TYPE", type);
                subs.put(
                        "FUNCTION_IDENTIFIER",
                        PostgresSelectIdentifiers.functionIdentifier(
                                tableName,
                                "_select_by_customer_id"));
            } else if (fkCategory.equalsIgnoreCase("application")) {
                String type = metadata.getColumnTypes().get("application_id");
                requireType("application_id", type);
                subs.put("APPLICATION_KEY_TYPE", type);
                subs.put(
                        "FUNCTION_IDENTIFIER",
                        PostgresSelectIdentifiers.functionIdentifier(
                                tableName,
                                "_select_by_application_id"));
            } else {
                throw new TemplateProcessingException(
                        "Other foreign-key selects require a specific foreign-key name");
            }
            for (Map.Entry<String, String> entry : subs.entrySet()) {
                baseTemplate = baseTemplate.replace("@x:scalar(text=" + entry.getKey() + ")", entry.getValue());
            }
            return baseTemplate;
        } catch (SQLException e) {
            throw new TemplateProcessingException("Error generating combined template", e);
        }
    }

    private static void requireType(String column, String type)
            throws TemplateProcessingException {
        if (type == null || type.isBlank()) {
            throw new TemplateProcessingException(
                    "No PostgreSQL type found for foreign key " + column);
        }
    }

    private static Map<String, String> generateScalars(Connection conn, String schema, String tableName, List<String> excludeColumns, String fkCategory) throws TemplateProcessingException {
        // All required scalar substitutions have been replaced in generateCombinedTemplate.
        return new HashMap<>();
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
