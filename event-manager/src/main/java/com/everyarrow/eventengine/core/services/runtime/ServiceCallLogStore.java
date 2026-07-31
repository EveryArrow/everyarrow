package com.everyarrow.eventengine.core.services.runtime;

import com.everyarrow.eventengine.spi.handler.ServiceCallHandlerException;
import com.everyarrow.utilities.json.JsonUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;

final class ServiceCallLogStore {

    private static final Logger logger = Logger.getLogger(ServiceCallLogStore.class.getName());
    private static final String CREATE_SQL =
            "SELECT event_manager.service_call_log_create(?::jsonb)::text AS result";
    private static final String UPDATE_SQL =
            "SELECT event_manager.service_call_log_update(?::jsonb)::text AS result";

    private final DatabaseConnectionProvider connectionProvider;

    ServiceCallLogStore(DatabaseConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    void createServiceCall(ServiceCall serviceCall) {
        if (serviceCall.getId() == null) {
            serviceCall.setId(UUID.randomUUID());
        }

        logger.info(() -> "Creating service call: " + summarizeServiceCall(serviceCall));
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement stmt = connection.prepareStatement(CREATE_SQL)) {

            stmt.setString(1, JsonUtils.getGson().toJson(createPayload(serviceCall)));
            stmt.executeQuery();
        } catch (SQLException e) {
            throw new ServiceCallHandlerException(e);
        }
    }

    void updateServiceCall(ServiceCall serviceCall) {
        if (serviceCall.getId() == null) {
            throw new ServiceCallHandlerException("ServiceCall missing UUID id during update");
        }

        logger.info(() -> "Updating service call: " + summarizeServiceCall(serviceCall));
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement stmt = connection.prepareStatement(UPDATE_SQL)) {

            stmt.setString(1, JsonUtils.getGson().toJson(updatePayload(serviceCall)));
            JsonObject result = executeJson(stmt);
            if (!booleanValue(result, "updated", true)) {
                createServiceCall(serviceCall);
                try (PreparedStatement retryStmt = connection.prepareStatement(UPDATE_SQL)) {
                    retryStmt.setString(1, JsonUtils.getGson().toJson(updatePayload(serviceCall)));
                    executeJson(retryStmt);
                }
            }
        } catch (SQLException e) {
            throw new ServiceCallHandlerException(e);
        }
    }

    private JsonObject createPayload(ServiceCall serviceCall) {
        JsonObject payload = basePayload(serviceCall);
        addString(payload, "registry_owner", resolveRegistryOwner(serviceCall));
        addNumber(payload, "source_id", resolveSourceId(serviceCall));
        addString(payload, "created_by_id", resolveActor(serviceCall));
        return payload;
    }

    private JsonObject updatePayload(ServiceCall serviceCall) {
        JsonObject payload = basePayload(serviceCall);
        addString(payload, "error", resolveError(serviceCall));
        addString(payload, "last_updated_by_id", resolveActor(serviceCall));
        return payload;
    }

    JsonObject basePayload(ServiceCall serviceCall) {
        JsonObject payload = new JsonObject();
        addString(payload, "id", String.valueOf(serviceCall.getId()));
        addString(payload, "event_engine_guid", String.valueOf(serviceCall.getId()));
        addString(payload, "service_type", serviceCall.getType());
        addString(payload, "source", serviceCall.getSource() == null ? null : serviceCall.getSource().name());
        addString(payload, "console_id", resolveConsoleId(serviceCall));
        addString(payload, "aws_event_id", metadataString(serviceCall, "aws_event_id"));
        addString(payload, "comments", metadataString(serviceCall, "comments"));
        payload.add("params", SensitiveJsonRedactor.redact(
                serviceCall.getParams() == null ? new JsonObject() : serviceCall.getParams()));
        return payload;
    }

    private JsonObject executeJson(PreparedStatement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return new JsonObject();
            }
            String raw = resultSet.getString("result");
            if (raw == null || raw.isBlank()) {
                return new JsonObject();
            }
            JsonElement parsed = JsonParser.parseString(raw);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        }
    }

    private boolean booleanValue(JsonObject object, String key, boolean fallback) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void addString(JsonObject object, String key, String value) {
        if (value != null && !value.isBlank()) {
            object.addProperty(key, value);
        }
    }

    private void addNumber(JsonObject object, String key, Number value) {
        if (value != null) {
            object.addProperty(key, value);
        }
    }

    private String resolveConsoleId(ServiceCall serviceCall) {
        String consoleId = metadataString(serviceCall, "console_id");
        if (consoleId == null || consoleId.isBlank()) {
            consoleId = metadataString(serviceCall, "source_id");
        }
        return consoleId == null || consoleId.isBlank() ? null : consoleId;
    }

    private Integer resolveSourceId(ServiceCall serviceCall) {
        String sourceId = metadataString(serviceCall, "source_id");
        if (sourceId == null || sourceId.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(sourceId);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String resolveRegistryOwner(ServiceCall serviceCall) {
        String type = serviceCall.getType();
        if (type != null) {
            int dotIndex = type.indexOf('.');
            if (dotIndex > 0) {
                return type.substring(0, dotIndex);
            }
        }
        ServiceCallSource source = serviceCall.getSource();
        if (source != null) {
            return source.name().toLowerCase(Locale.ROOT);
        }
        return "runtime";
    }

    private String resolveActor(ServiceCall serviceCall) {
        if (serviceCall.getSecurityContext() != null) {
            SecurityContext securityContext = serviceCall.getSecurityContext();
            if (securityContext.getAuthProviderId() != null && !securityContext.getAuthProviderId().isBlank()) {
                return securityContext.getAuthProviderId();
            }
            if (securityContext.getPrincipalId() != null && !securityContext.getPrincipalId().isBlank()) {
                return securityContext.getPrincipalId();
            }
        }
        if (serviceCall.getParams() != null && serviceCall.getParams().has("auth_provider_id")) {
            try {
                return serviceCall.getParams().get("auth_provider_id").getAsString();
            } catch (Exception ignored) {
            }
        }
        return "system";
    }

    private String resolveError(ServiceCall serviceCall) {
        String error = metadataString(serviceCall, "error");
        if (error != null && !error.isBlank()) {
            return error;
        }
        return null;
    }

    private String metadataString(ServiceCall serviceCall, String key) {
        if (serviceCall == null || key == null) {
            return null;
        }
        Object value = serviceCall.getMetadata().get(key);
        if (value == null) {
            return null;
        }
        String stringValue = String.valueOf(value);
        return stringValue.isBlank() ? null : stringValue;
    }

    private String summarizeServiceCall(ServiceCall serviceCall) {
        if (serviceCall == null) {
            return "null";
        }
        return "id=" + serviceCall.getId()
                + ", dbId=" + serviceCall.getDbId()
                + ", type=" + serviceCall.getType()
                + ", source=" + serviceCall.getSource();
    }
}
