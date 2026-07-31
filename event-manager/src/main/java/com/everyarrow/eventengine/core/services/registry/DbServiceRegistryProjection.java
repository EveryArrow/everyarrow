/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.registry;

import com.everyarrow.eventengine.core.services.runtime.GenericProcessHandler;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.google.gson.Gson;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

record DbServiceRegistryProjection(
        String serviceType,
        String registryEntryKind,
        String databaseName,
        String schemaName,
        String tableName,
        String functionName,
        String functionType,
        String selector,
        String selectorColumnsJson,
        String jsonType,
        String httpMethod,
        String routePath,
        String annotationsJson,
        String description,
        String source,
        boolean functionExists,
        String handlerClass,
        String targetMethodName,
        String category,
        String pluginId,
        String surfaceId,
        String metadataHash) {

    static final String KIND_DB_FUNCTION = "db_function";
    static final String KIND_JAVA_HANDLER = "java_handler";
    static final String KIND_ANNOTATION_METADATA = "annotation_metadata";

    private static final Gson GSON = new Gson();
    private static final String DEFAULT_SELECTOR_COLUMNS = "[]";

    static Optional<DbServiceRegistryProjection> from(ServiceCallDefinition definition, String surfaceId) {
        if (definition == null || isBlank(definition.getServiceType())) {
            return Optional.empty();
        }

        String handlerClass = firstNonBlank(definition.getHandlerClass(), definition.getClassName());
        if (isGenericProcessHandler(definition, handlerClass)) {
            return dbFunctionProjection(definition, handlerClass, surfaceId);
        }

        if (isBlank(handlerClass) || !hasAnnotations(definition)) {
            return Optional.empty();
        }

        return Optional.of(javaHandlerProjection(definition, handlerClass, surfaceId));
    }

    private static Optional<DbServiceRegistryProjection> dbFunctionProjection(
            ServiceCallDefinition definition,
            String handlerClass,
            String surfaceId) {
        DbFunctionParts parts = DbFunctionParts.from(definition);
        if (isBlank(parts.databaseName()) || isBlank(parts.schemaName()) || isBlank(parts.functionName())) {
            return Optional.empty();
        }

        String source = firstNonBlank(definition.getSource(), "db:" + parts.databaseName());
        String annotationsJson = annotationsJson(definition);
        String httpMethod = restMethod(definition);
        String routePath = restPath(definition);
        String description = description(definition);
        String jsonType = firstNonBlank(definition.getJsonType(), "json");

        DbServiceRegistryProjection projection = new DbServiceRegistryProjection(
                definition.getServiceType(),
                KIND_DB_FUNCTION,
                parts.databaseName(),
                parts.schemaName(),
                firstNonBlank(parts.tableName(), "registry_function"),
                parts.functionName(),
                "db_function",
                null,
                DEFAULT_SELECTOR_COLUMNS,
                jsonType,
                httpMethod,
                routePath,
                annotationsJson,
                description,
                source,
                true,
                firstNonBlank(handlerClass, GenericProcessHandler.class.getSimpleName()),
                definition.getTargetMethodName(),
                firstNonBlank(definition.getCategory(), "Database"),
                pluginId(source),
                normalizeSurfaceId(surfaceId),
                null
        );
        return Optional.of(projection.withMetadataHash());
    }

    private static DbServiceRegistryProjection javaHandlerProjection(
            ServiceCallDefinition definition,
            String handlerClass,
            String surfaceId) {
        String annotationsJson = annotationsJson(definition);
        String httpMethod = restMethod(definition);
        String routePath = restPath(definition);
        String description = description(definition);
        String source = firstNonBlank(definition.getSource(), "core");

        DbServiceRegistryProjection projection = new DbServiceRegistryProjection(
                definition.getServiceType(),
                KIND_JAVA_HANDLER,
                "event_manager",
                "event_manager",
                "java_handler",
                firstNonBlank(definition.getTargetMethodName(), definition.getServiceType()),
                KIND_JAVA_HANDLER,
                null,
                DEFAULT_SELECTOR_COLUMNS,
                firstNonBlank(definition.getJsonType(), "json"),
                httpMethod,
                routePath,
                annotationsJson,
                description,
                source,
                true,
                handlerClass,
                definition.getTargetMethodName(),
                firstNonBlank(definition.getCategory(), "Generic"),
                pluginId(source),
                normalizeSurfaceId(surfaceId),
                null
        );
        return projection.withMetadataHash();
    }

    private DbServiceRegistryProjection withMetadataHash() {
        return new DbServiceRegistryProjection(
                serviceType,
                registryEntryKind,
                databaseName,
                schemaName,
                tableName,
                functionName,
                functionType,
                selector,
                selectorColumnsJson,
                jsonType,
                httpMethod,
                routePath,
                annotationsJson,
                description,
                source,
                functionExists,
                handlerClass,
                targetMethodName,
                category,
                pluginId,
                surfaceId,
                sha256(canonicalMetadata())
        );
    }

    private String canonicalMetadata() {
        return String.join("\u001F",
                blankToEmpty(serviceType),
                blankToEmpty(registryEntryKind),
                blankToEmpty(databaseName),
                blankToEmpty(schemaName),
                blankToEmpty(tableName),
                blankToEmpty(functionName),
                blankToEmpty(functionType),
                blankToEmpty(selector),
                blankToEmpty(selectorColumnsJson),
                blankToEmpty(jsonType),
                blankToEmpty(httpMethod),
                blankToEmpty(routePath),
                blankToEmpty(annotationsJson),
                blankToEmpty(description),
                blankToEmpty(source),
                Boolean.toString(functionExists),
                blankToEmpty(handlerClass),
                blankToEmpty(targetMethodName),
                blankToEmpty(category),
                blankToEmpty(pluginId),
                blankToEmpty(surfaceId));
    }

    private static boolean isGenericProcessHandler(ServiceCallDefinition definition, String handlerClass) {
        return GenericProcessHandler.class.getSimpleName().equals(handlerClass)
                || GenericProcessHandler.class.getName().equals(handlerClass)
                || (!isBlank(definition.getSource()) && definition.getSource().startsWith("db:"));
    }

    private static String annotationsJson(ServiceCallDefinition definition) {
        ServiceCallDefinition.Annotations annotations = definition.getAnnotations();
        if (annotations == null || !hasAnnotations(definition)) {
            return null;
        }
        return GSON.toJson(annotations);
    }

    private static boolean hasAnnotations(ServiceCallDefinition definition) {
        return definition.getAnnotations() != null && definition.hasAnnotations();
    }

    private static String restMethod(ServiceCallDefinition definition) {
        ServiceCallDefinition.RestRouteMeta route = definition.getRestRoute();
        return route == null ? null : route.method;
    }

    private static String restPath(ServiceCallDefinition definition) {
        ServiceCallDefinition.RestRouteMeta route = definition.getRestRoute();
        return route == null ? null : route.path;
    }

    private static String description(ServiceCallDefinition definition) {
        ServiceCallDefinition.Annotations annotations = definition.getAnnotations();
        if (annotations == null) {
            return null;
        }
        if (annotations.perch != null && !isBlank(annotations.perch.description)) {
            return annotations.perch.description;
        }
        if (annotations.monitored != null && !isBlank(annotations.monitored.description)) {
            return annotations.monitored.description;
        }
        return null;
    }

    private static String pluginId(String source) {
        if (isBlank(source) || !source.startsWith("plugin:")) {
            return null;
        }
        String remainder = source.substring("plugin:".length());
        int separator = remainder.indexOf(':');
        return separator < 0 ? remainder : remainder.substring(0, separator);
    }

    private static String normalizeSurfaceId(String surfaceId) {
        return isBlank(surfaceId) ? "default" : surfaceId.trim();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is not available", e);
        }
    }

    private static String firstNonBlank(String primary, String secondary) {
        return isBlank(primary) ? secondary : primary;
    }

    private static String blankToEmpty(String value) {
        return isBlank(value) ? "" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record DbFunctionParts(String databaseName, String schemaName, String tableName, String functionName) {
        private static DbFunctionParts from(ServiceCallDefinition definition) {
            String databaseName = firstNonBlank(definition.getDbDatabase(), databaseFromSource(definition.getSource()));
            String schemaName = definition.getDbSchema();
            String functionName = definition.getDbFunction();

            String[] parts = definition.getServiceType().split("\\.");
            if (isBlank(functionName) && parts.length >= 1) {
                functionName = parts[parts.length - 1];
            }
            if (isBlank(schemaName) && parts.length >= 2) {
                schemaName = parts[parts.length - 2];
            }
            if (isBlank(databaseName) && parts.length >= 3) {
                databaseName = parts[parts.length - 3];
            }

            return new DbFunctionParts(databaseName, schemaName, null, functionName);
        }

        private static String databaseFromSource(String source) {
            if (isBlank(source) || !source.startsWith("db:")) {
                return null;
            }
            return source.substring("db:".length());
        }
    }
}
