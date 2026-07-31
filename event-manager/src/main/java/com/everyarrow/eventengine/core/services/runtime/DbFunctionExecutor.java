package com.everyarrow.eventengine.core.services.runtime;

import com.everyarrow.eventengine.core.routing.ServiceCallFactory;
import com.everyarrow.eventengine.spi.handler.ServiceCallHandlerException;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

final class DbFunctionExecutor {

    private final DatabaseConnectionProvider connectionProvider;

    DbFunctionExecutor(DatabaseConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    String process(ServiceCall serviceCall, String schema) {
        String eventType = serviceCall.getType();
        String effectiveSchema = schema == null || schema.isBlank() ? "public" : schema;
        String sql = String.format("select * from %s.%s(?::json);", quoteIdent(effectiveSchema), quoteIdent(eventType));

        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setString(1, serviceCall.getParams().toString());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getString(1);
            }

            throw new ServiceCallHandlerException("Internal Server Error: No results returned.");
        } catch (SQLException e) {
            throw new ServiceCallHandlerException(e);
        }
    }

    JsonObject process(String serviceType, String typeName, JsonObject params) {
        ServiceCall serviceCall = new ServiceCall();
        serviceCall.setType(typeName);
        serviceCall.setParams(params);

        String schema = serviceType == null || serviceType.isBlank() ? "public" : serviceType.toLowerCase();
        String resultJson = process(serviceCall, schema);

        try {
            return JsonParser.parseString(resultJson).getAsJsonObject();
        } catch (Exception e) {
            throw new ServiceCallHandlerException("Invalid JSON returned from database for eventType=" + typeName, e);
        }
    }

    String process(ServiceCall serviceCall) {
        return process(serviceCall, "public");
    }

    JsonObject processDbFunction(String dbName,
                                 String schema,
                                 String function,
                                 String jsonType,
                                 JsonObject params) {
        JsonElement element = processDbFunctionElement(dbName, schema, function, jsonType, params);
        return asObject(element);
    }

    JsonObject processDbFunction(String dbName,
                                 String schema,
                                 String function,
                                 JsonObject params) {
        JsonElement element = processDbFunctionElement(dbName, schema, function, params);
        return asObject(element);
    }

    JsonElement processDbFunctionElement(String dbName,
                                         String schema,
                                         String function,
                                         String jsonType,
                                         JsonObject params) {
        String castType = jsonType != null && jsonType.equalsIgnoreCase("jsonb") ? "jsonb" : "json";
        return processDbFunctionElementWithCast(dbName, schema, function, castType, params);
    }

    JsonElement processDbFunctionElement(String dbName,
                                         String schema,
                                         String function,
                                         JsonObject params) {
        return processDbFunctionElementWithCast(dbName, schema, function, "json", params);
    }

    private JsonElement processDbFunctionElementWithCast(String dbName,
                                                         String schema,
                                                         String function,
                                                         String castType,
                                                         JsonObject params) {
        if (schema == null || schema.isBlank()) {
            throw new ServiceCallHandlerException("schema is required");
        }
        if (function == null || function.isBlank()) {
            throw new ServiceCallHandlerException("function is required");
        }

        String input = toDatabaseJson(params);

        try (Connection connection = connectionProvider.getConnection(dbName)) {
            try (var searchPath = connection.createStatement()) {
                searchPath.execute("SET search_path = " + quoteIdent(schema) + ", public");
            }
            try {
                return executeDbFunction(connection, schema, function, input, castType);
            } catch (SQLException firstEx) {
                if (!"jsonb".equals(castType) || !shouldRetryWithJson(firstEx)) {
                    throw firstEx;
                }
                return executeDbFunction(connection, schema, function, input, "json");
            }
        } catch (SQLException e) {
            throw new ServiceCallHandlerException(e);
        }
    }

    private JsonElement executeDbFunction(Connection connection,
                                          String schema,
                                          String function,
                                          String input,
                                          String inputType) throws SQLException {
        String sql = String.format(
                "select * from %s.%s(?::%s);",
                quoteIdent(schema),
                quoteIdent(function),
                inputType
        );

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, input);

            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) {
                return new JsonObject();
            }

            Object raw = rs.getObject(1);
            if (raw == null) {
                return new JsonObject();
            }

            String out = extractJsonText(raw);
            if (out == null || out.isBlank()) {
                return new JsonObject();
            }

            try {
                JsonElement element = JsonParser.parseString(out);
                return element == null || element.isJsonNull() ? new JsonObject() : element;
            } catch (Exception parseEx) {
                throw new ServiceCallHandlerException("Invalid JSON returned from database function", parseEx);
            }
        }
    }

    private JsonObject asObject(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return new JsonObject();
        }
        if (element.isJsonObject()) {
            return element.getAsJsonObject();
        }

        JsonObject wrapper = new JsonObject();
        wrapper.add("data", element);
        return wrapper;
    }

    private boolean shouldRetryWithJson(SQLException ex) {
        String message = ex.getMessage();
        if (message == null) {
            return false;
        }
        String lowered = message.toLowerCase();
        return lowered.contains("does not exist") && lowered.contains("jsonb");
    }

    private String toDatabaseJson(JsonObject params) {
        if (params == null) {
            return "{}";
        }

        JsonElement rawArray = params.get(ServiceCallFactory.RAW_ARRAY_BODY_KEY);
        if (rawArray != null && rawArray.isJsonArray() && params.size() == 1) {
            return rawArray.toString();
        }

        return params.toString();
    }

    private String extractJsonText(Object raw) {
        try {
            if (raw != null && raw.getClass().getName().equals("org.postgresql.util.PGobject")) {
                Object value = raw.getClass().getMethod("getValue").invoke(raw);
                return value == null ? null : value.toString();
            }
        } catch (Exception ignored) {
        }
        return raw.toString();
    }

    private String quoteIdent(String ident) {
        String value = ident == null ? "" : ident.trim();
        if (value.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
