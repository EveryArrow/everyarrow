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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SelectProvider extends TemplateProvider {

    /**
     * selectType: "single" for PK-based select, "multi" for FK-based select.
     */
    public SelectProvider(String selectType, PromptProvider promptProvider,
                          Connection conn, String schema, String tableName, List<String> excludeColumns)
            throws TemplateProcessingException {
        super(generateCombinedTemplate(selectType, conn, schema, tableName, excludeColumns),
                generateScalars(selectType, conn, schema, tableName, excludeColumns),
                getDefaultLists(), getDefaultPrompts(promptProvider));
    }

    public SelectProvider(String selectType,
                          Connection conn, String schema, String tableName, List<String> excludeColumns)
            throws TemplateProcessingException {
        this(selectType, null, conn, schema, tableName, excludeColumns);
    }

    private static String generateCombinedTemplate(String selectType, Connection conn, String schema, String tableName, List<String> excludeColumns)
            throws TemplateProcessingException {
        try {
            MetadataUtils.TableMetadata metadata = MetadataUtils.getTableMetadata(conn, schema, tableName);
            if ("single".equalsIgnoreCase(selectType)) {
                // Single record select by primary key.
                List<String> pkColumns = metadata.getPrimaryKeys();
                if (pkColumns.isEmpty()) {
                    throw new TemplateProcessingException("No primary key found for table " + tableName);
                }
                String pk = pkColumns.get(0);
                String baseTemplate = TemplateSelectOne.getTemplate();
                return applySubstitutions(
                        baseTemplate,
                        SelectTemplateParameterGenerator.generateSubstitutions(
                                tableName,
                                metadata.getAllColumns(),
                                excludeColumns,
                                pk,
                                metadata.getColumnTypes()));
            } else if ("multi".equalsIgnoreCase(selectType)) {
                // Multi-select by foreign key.
                List<String> fkColumns = MetadataUtils.getForeignKeys(conn, schema, tableName);
                if (fkColumns.isEmpty()) {
                    throw new TemplateProcessingException("No foreign keys found for table " + tableName);
                }
                // Prioritize: application_id, customer_id, then others.
                List<String> selectedFks = new ArrayList<>();
                if (fkColumns.contains("application_id")) {
                    selectedFks.add("application_id");
                }
                if (fkColumns.contains("customer_id")) {
                    selectedFks.add("customer_id");
                }
                for (String fk : fkColumns) {
                    if (!fk.equalsIgnoreCase("application_id") && !fk.equalsIgnoreCase("customer_id")) {
                        selectedFks.add(fk);
                    }
                }
                // Build SELECT columns list.
                String selectColumns = quotedColumns(metadata.getAllColumns(), excludeColumns);
                String baseTemplate = TemplateSelectByMany.getTemplate();
                StringBuilder combined = new StringBuilder();
                for (String fk : selectedFks) {
                    String fkType = metadata.getColumnTypes().get(fk);
                    if (fkType == null || fkType.isBlank()) {
                        throw new TemplateProcessingException(
                                "No PostgreSQL type found for foreign key " + fk);
                    }
                    Map<String, String> subs = new HashMap<>();
                    subs.put(
                            "FUNCTION_IDENTIFIER",
                            PostgresSelectIdentifiers.functionIdentifier(
                                    tableName,
                                    "_select_by_" + fk));
                    subs.put(
                            "TABLE_IDENTIFIER",
                            PostgresSelectIdentifiers.quoteIdentifier(tableName));
                    subs.put(
                            "FK_IDENTIFIER",
                            PostgresSelectIdentifiers.quoteIdentifier(fk));
                    subs.put("FK_TYPE", fkType);
                    subs.put("COLUMN_NAMES", selectColumns);
                    combined.append(applySubstitutions(baseTemplate, subs)).append("\n\n");
                }
                return combined.toString();
            } else {
                throw new TemplateProcessingException("Invalid select type: " + selectType);
            }
        } catch (SQLException e) {
            throw new TemplateProcessingException("Error generating combined template", e);
        }
    }

    private static String quotedColumns(List<String> allColumns, List<String> excludeColumns) {
        java.util.StringJoiner columns = new java.util.StringJoiner(", ");
        for (String column : allColumns) {
            if (excludeColumns == null || !excludeColumns.contains(column)) {
                columns.add(PostgresSelectIdentifiers.quoteIdentifier(column));
            }
        }
        return columns.toString();
    }

    private static String applySubstitutions(String template, Map<String, String> substitutions) {
        String result = template;
        for (Map.Entry<String, String> entry : substitutions.entrySet()) {
            result = result.replace(
                    "@x:scalar(text=" + entry.getKey() + ")",
                    entry.getValue());
        }
        return result;
    }

    private static Map<String, String> generateScalars(String selectType, Connection conn, String schema, String tableName, List<String> excludeColumns)
            throws TemplateProcessingException {
        // All substitutions are done in the template generation.
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
