package com.everyarrow.eventengine.core.services.runtime;

import com.everyarrow.eventengine.spi.handler.ServiceCallHandler;
import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.UUID;

/**
 * Generic handler used for DB-discovered services.
 *
 * If serviceType ends with: db.schema.function
 * then execute the Postgres function schema.function(jsonb) on the target db.
 */
public class GenericProcessHandler implements ServiceCallHandler {

    private final PostgresHelper pg;
    private Engine engine;

    public GenericProcessHandler() {
        this(null, new PostgresHelper());
    }

    public GenericProcessHandler(Engine engine) {
        this(engine, new PostgresHelper());
    }

    public GenericProcessHandler(Engine engine, PostgresHelper pg) {
        this.engine = engine;
        this.pg = pg;
    }

    @Override
    public JsonElement execute(ServiceCall call) throws Exception {
        JsonObject params = call == null ? null : call.getParams();
        if (supportsPerchCorrelation(call == null ? null : call.getType())) {
            params = withPerchCorrelation(params, call);
        }
        String type = call == null ? null : call.getType();
        String source = call == null || call.getSource() == null ? null : call.getSource().name();
        return execute(params, type, source);
    }

    @Override
    public JsonObject execute(JsonObject params, String type, String source) throws Exception {

        String jsonType = "json";
        String database = null;
        String schema = null;
        String function = null;
        if (engine != null && engine.getRegistry() != null && type != null) {
            com.everyarrow.eventengine.spi.model.ServiceCallDefinition def = engine.getRegistry().getDefinition(type);
            if (def != null) {
                if (def.getJsonType() != null) {
                    jsonType = def.getJsonType();
                }
                database = firstNonBlank(def.getDbDatabase(), database);
                schema = firstNonBlank(def.getDbSchema(), schema);
                function = firstNonBlank(def.getDbFunction(), function);
                if (def.getSource() != null && def.getSource().startsWith("db:")) {
                    database = firstNonBlank(database, def.getSource().substring(3));
                }
            }
        }

        if (database != null && schema != null && function != null) {
            return pg.processDbFunction(database, schema, function, jsonType, params);
        }

        // DB-discovered serviceType convention: [namespace....]db.schema.function
        if (type != null) {
            String[] parts = type.split("\\.");
            if (parts.length == 2 && database != null) {
                String fallbackSchema = parts[0];
                String fn = parts[1];
                return pg.processDbFunction(database, fallbackSchema, fn, jsonType, params);
            } else if (parts.length >= 3 && database != null) {
                String fallbackSchema = parts[parts.length - 2];
                String fn = parts[parts.length - 1];
                return pg.processDbFunction(database, fallbackSchema, fn, jsonType, params);
            } else if (parts.length >= 3) {
                String db = database == null ? parts[parts.length - 3] : database;
                String fallbackSchema = parts[parts.length - 2];
                String fn = parts[parts.length - 1];
                return pg.processDbFunction(db, fallbackSchema, fn, jsonType, params);
            }
        }

        // fallback: preserve prior placeholder behavior
        JsonObject result = new JsonObject();
        result.addProperty("type", type);
        result.addProperty("source", source == null ? "unknown" : source);
        if (params != null) {
            result.add("params", params);
        } else {
            result.add("params", new JsonObject());
        }
        result.addProperty("message", "genericProcess executed (placeholder)");
        return result;
    }

    private String firstNonBlank(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }

    private boolean supportsPerchCorrelation(String serviceType) {
        if (engine == null || engine.getRegistry() == null || serviceType == null) {
            return false;
        }
        ServiceCallDefinition definition = engine.getRegistry().getDefinition(serviceType);
        if (definition == null
                || definition.getAnnotations() == null
                || definition.getAnnotations().databaseCorrelation == null) {
            return false;
        }
        ServiceCallDefinition.DatabaseCorrelationMeta correlation =
                definition.getAnnotations().databaseCorrelation;
        return PerchRuntimeMetadata.DATABASE_CORRELATION_COLUMN.equalsIgnoreCase(correlation.column)
                && PerchRuntimeMetadata.DATABASE_CORRELATION_SCOPE.equalsIgnoreCase(correlation.scope);
    }

    private JsonObject withPerchCorrelation(JsonObject original, ServiceCall call) {
        JsonObject enriched = original == null ? new JsonObject() : original.deepCopy();
        enriched.remove(PerchRuntimeMetadata.DATABASE_CORRELATION_COLUMN);

        String executionId = PerchRuntimeMetadata.mostSpecificExecutionId(call);
        if (executionId == null) {
            return enriched;
        }
        try {
            UUID.fromString(executionId);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(
                    "Perch execution metadata must be a UUID before it can be persisted", invalid);
        }
        enriched.addProperty(PerchRuntimeMetadata.DATABASE_CORRELATION_COLUMN, executionId);
        return enriched;
    }

    @Override
    public JsonObject onError(ServiceCall call, Exception ex) {
        JsonObject err = new JsonObject();
        err.addProperty("error", "Internal server error");
        return err;
    }
}
