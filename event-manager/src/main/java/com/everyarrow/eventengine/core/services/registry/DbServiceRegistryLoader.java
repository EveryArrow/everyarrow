package com.everyarrow.eventengine.core.services.registry;

import com.everyarrow.eventengine.spi.model.DbDiscoveredService;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads DB-discovered services from the meta registry database.
 *
 * Event Manager owns registry consumption. PostgreSQL owns the registry table
 * shape and exposes it through event_manager.load_registry_surface(jsonb).
 */
public final class DbServiceRegistryLoader {
    private static final Gson GSON = new Gson();
    private static final String REGISTRY_FUNCTION_SOURCE = "event_manager.load_registry_surface";
    private static final String LOAD_SQL =
            "SELECT event_manager.load_registry_surface(?::jsonb)::text AS result";

    private DbServiceRegistryLoader() {
    }

    public static List<ServiceCallDefinition> loadAllFromMetaDb(Connection metaConn) throws Exception {
        return loadAllFromMetaDb(metaConn, null);
    }

    public static List<ServiceCallDefinition> loadAllFromMetaDb(Connection metaConn, String surfaceId) throws Exception {
        try (PreparedStatement statement = metaConn.prepareStatement(LOAD_SQL)) {
            statement.setString(1, payloadJson(surfaceId));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return List.of();
                }
                return loadFromRows(rowsFromResult(resultSet.getString("result")), surfaceId);
            }
        }
    }

    private static String payloadJson(String surfaceId) {
        JsonObject payload = new JsonObject();
        payload.addProperty("surface_id", normalizeSurfaceId(surfaceId));
        return GSON.toJson(payload);
    }

    private static List<ServiceCallDefinition> loadFromRows(JsonArray rows, String surfaceId) throws Exception {
        List<ServiceCallDefinition> out = new ArrayList<>();
        for (JsonElement element : rows) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject row = element.getAsJsonObject();
            if (!shouldLoad(row, surfaceId)) {
                continue;
            }
            ServiceCallDefinition definition = definitionFromRow(row);
            if (isDbFunctionRow(row)) {
                DbServiceRegistrationPolicy.validate(definition, REGISTRY_FUNCTION_SOURCE);
            }
            out.add(definition);
        }
        return out;
    }

    private static JsonArray rowsFromResult(String rawResult) {
        if (rawResult == null || rawResult.isBlank()) {
            return new JsonArray();
        }

        JsonElement parsed = JsonParser.parseString(rawResult);
        if (parsed.isJsonArray()) {
            return parsed.getAsJsonArray();
        }
        if (parsed.isJsonObject()) {
            JsonObject object = parsed.getAsJsonObject();
            JsonElement rows = firstPresent(object, "rows", "entries", "registry_functions");
            if (rows != null && rows.isJsonArray()) {
                return rows.getAsJsonArray();
            }
        }
        return new JsonArray();
    }

    private static ServiceCallDefinition definitionFromRow(JsonObject row) {
        if (isJavaHandlerRow(row)) {
            return ServiceCallDefinition.fromRegistryHandler(
                    string(row, "service_type"),
                    string(row, "handler_class"),
                    string(row, "category"),
                    string(row, "source"),
                    string(row, "json_type"),
                    string(row, "target_method_name"),
                    string(row, "http_method"),
                    string(row, "route_path"),
                    string(row, "description"),
                    parseAnnotations(row.get("annotations")));
        }

        DbDiscoveredService discovered = new DbDiscoveredService(
                firstNonBlank(string(row, "database_name"), string(row, "db_name")),
                string(row, "schema_name"),
                string(row, "function_name"),
                string(row, "service_type"),
                string(row, "http_method"),
                string(row, "route_path"),
                string(row, "description"),
                null,
                string(row, "json_type"),
                parseAnnotations(row.get("annotations")));

        return ServiceCallDefinition.fromDb(discovered);
    }

    private static boolean shouldLoad(JsonObject row, String requestedSurfaceId) {
        if (!booleanValue(row, "is_active", true)) {
            return false;
        }
        if (isBlank(string(row, "service_type"))) {
            return false;
        }
        if (!shouldLoadForSurface(string(row, "surface_id"), requestedSurfaceId)) {
            return false;
        }

        if (isJavaHandlerRow(row)) {
            return !booleanValue(row, "missing_from_surface", false);
        }
        if (isDbFunctionRow(row)) {
            return !isBlank(firstNonBlank(string(row, "database_name"), string(row, "db_name")))
                    && !isBlank(string(row, "schema_name"))
                    && !isBlank(string(row, "function_name"));
        }
        return false;
    }

    private static boolean shouldLoadForSurface(String rowSurfaceId, String requestedSurfaceId) {
        if (rowSurfaceId == null || rowSurfaceId.isBlank()) {
            return true;
        }
        if (requestedSurfaceId == null || requestedSurfaceId.isBlank()) {
            return true;
        }
        return rowSurfaceId.equalsIgnoreCase(requestedSurfaceId.trim());
    }

    private static boolean isJavaHandlerRow(JsonObject row) {
        return DbServiceRegistryProjection.KIND_JAVA_HANDLER.equalsIgnoreCase(registryEntryKind(row));
    }

    private static boolean isDbFunctionRow(JsonObject row) {
        return DbServiceRegistryProjection.KIND_DB_FUNCTION.equalsIgnoreCase(registryEntryKind(row));
    }

    private static String registryEntryKind(JsonObject row) {
        return firstNonBlank(string(row, "registry_entry_kind"), DbServiceRegistryProjection.KIND_DB_FUNCTION);
    }

    private static ServiceCallDefinition.Annotations parseAnnotations(JsonElement rawAnnotations) {
        if (rawAnnotations == null || rawAnnotations.isJsonNull()) {
            return null;
        }
        if (rawAnnotations.isJsonPrimitive()) {
            String value = rawAnnotations.getAsString();
            if (value == null || value.isBlank()) {
                return null;
            }
            return GSON.fromJson(value, ServiceCallDefinition.Annotations.class);
        }
        return GSON.fromJson(rawAnnotations, ServiceCallDefinition.Annotations.class);
    }

    private static JsonElement firstPresent(JsonObject object, String... names) {
        if (object == null || names == null) {
            return null;
        }
        for (String name : names) {
            if (object.has(name) && !object.get(name).isJsonNull()) {
                return object.get(name);
            }
        }
        return null;
    }

    private static boolean booleanValue(JsonObject object, String key, boolean fallback) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String string(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        try {
            String value = object.get(key).getAsString();
            return value == null || value.isBlank() ? null : value;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String firstNonBlank(String primary, String secondary) {
        return isBlank(primary) ? secondary : primary;
    }

    private static String normalizeSurfaceId(String surfaceId) {
        return isBlank(surfaceId) ? "default" : surfaceId.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
