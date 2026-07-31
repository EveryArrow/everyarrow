/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.registry;

import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Refreshes discovered registry metadata through the database-owned refresh function.
 *
 * Event Manager owns discovery and projection. PostgreSQL owns registry table writes.
 */
public final class DbServiceRegistryRefresher {

    private static final Gson GSON = new Gson();
    private static final String REFRESH_SQL =
            "SELECT event_manager.refresh_registry_surface(?::jsonb)::text AS result";

    public int refresh(Connection metaConnection,
                       Collection<ServiceCallDefinition> discoveredDefinitions,
                       Collection<Class<?>> loadedClasses,
                       Map<String, String> classSources,
                       String surfaceId) throws SQLException {
        Objects.requireNonNull(metaConnection, "metaConnection is required");

        Map<String, DbServiceRegistryProjection> projectionsByServiceType =
                project(discoveredDefinitions, loadedClasses, classSources, surfaceId);
        // An empty surface is still a meaningful refresh: PostgreSQL must
        // deactivate rows that this server no longer discovers.
        return refreshViaDbFunction(
                metaConnection,
                normalizeSurfaceId(surfaceId),
                projectionsByServiceType.values());
    }

    private Map<String, DbServiceRegistryProjection> project(
            Collection<ServiceCallDefinition> discoveredDefinitions,
            Collection<Class<?>> loadedClasses,
            Map<String, String> classSources,
            String surfaceId) {
        LinkedHashMap<String, DbServiceRegistryProjection> projections = new LinkedHashMap<>();

        if (discoveredDefinitions != null && !discoveredDefinitions.isEmpty()) {
            for (ServiceCallDefinition definition : discoveredDefinitions) {
                DbServiceRegistryProjection.from(definition, surfaceId)
                        .ifPresent(projection -> projections.putIfAbsent(projection.serviceType(), projection));
            }
        }

        for (DbServiceRegistryProjection projection :
                DbAnnotationMetadataProjection.fromClasses(loadedClasses, classSources, surfaceId)) {
            projections.putIfAbsent(projection.serviceType(), projection);
        }
        return projections;
    }

    private int refreshViaDbFunction(Connection metaConnection,
                                     String surfaceId,
                                     Collection<DbServiceRegistryProjection> projections) throws SQLException {
        String payload = payloadJson(surfaceId, projections);
        try (PreparedStatement statement = metaConnection.prepareStatement(REFRESH_SQL)) {
            statement.setString(1, payload);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return refreshedCount(resultSet.getString("result"), projections.size());
                }
            }
        }
        return projections.size();
    }

    private String payloadJson(String surfaceId, Collection<DbServiceRegistryProjection> projections) {
        JsonObject payload = new JsonObject();
        payload.addProperty("surface_id", surfaceId);

        JsonArray entries = new JsonArray();
        for (DbServiceRegistryProjection projection : projections) {
            entries.add(entryJson(projection));
        }
        payload.add("entries", entries);
        return GSON.toJson(payload);
    }

    private JsonObject entryJson(DbServiceRegistryProjection projection) {
        JsonObject entry = new JsonObject();
        addString(entry, "service_type", projection.serviceType());
        addString(entry, "registry_entry_kind", projection.registryEntryKind());
        addString(entry, "database_name", projection.databaseName());
        addString(entry, "schema_name", projection.schemaName());
        addString(entry, "table_name", projection.tableName());
        addString(entry, "function_name", projection.functionName());
        addString(entry, "function_type", projection.functionType());
        addString(entry, "selector", projection.selector());
        entry.add("selector_columns", jsonOrNull(projection.selectorColumnsJson()));
        addString(entry, "json_type", projection.jsonType());
        addString(entry, "http_method", projection.httpMethod());
        addString(entry, "route_path", projection.routePath());
        entry.add("annotations", jsonOrNull(projection.annotationsJson()));
        addString(entry, "description", projection.description());
        addString(entry, "source", projection.source());
        entry.addProperty("function_exists", projection.functionExists());
        addString(entry, "handler_class", projection.handlerClass());
        addString(entry, "target_method_name", projection.targetMethodName());
        addString(entry, "category", projection.category());
        addString(entry, "plugin_id", projection.pluginId());
        addString(entry, "surface_id", projection.surfaceId());
        addString(entry, "metadata_hash", projection.metadataHash());
        return entry;
    }

    private void addString(JsonObject object, String name, String value) {
        if (value == null || value.isBlank()) {
            object.add(name, JsonNull.INSTANCE);
            return;
        }
        object.addProperty(name, value);
    }

    private JsonElement jsonOrNull(String value) {
        if (value == null || value.isBlank()) {
            return JsonNull.INSTANCE;
        }
        return JsonParser.parseString(value);
    }

    private int refreshedCount(String resultJson, int fallback) {
        if (resultJson == null || resultJson.isBlank()) {
            return fallback;
        }

        JsonElement parsed = JsonParser.parseString(resultJson);
        if (!parsed.isJsonObject()) {
            return fallback;
        }

        JsonObject result = parsed.getAsJsonObject();
        JsonElement count = result.get("refreshed_count");
        if (count == null || !count.isJsonPrimitive() || !count.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        return count.getAsInt();
    }

    private String normalizeSurfaceId(String surfaceId) {
        return surfaceId == null || surfaceId.isBlank() ? "default" : surfaceId.trim();
    }
}
