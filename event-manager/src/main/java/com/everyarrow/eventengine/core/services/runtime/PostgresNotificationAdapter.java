/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.runtime;

import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.eventengine.core.adapters.ProtocolAdapter;
import com.everyarrow.eventengine.core.config.EventManagerOperatingMode;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.everyarrow.utilities.db.notifications.PostgresNotificationConfig;
import com.everyarrow.utilities.db.notifications.PostgresNotificationListener;
import com.everyarrow.utilities.db.notifications.PostgresNotificationMessage;
import com.everyarrow.utilities.environment.Environment;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class PostgresNotificationAdapter implements ProtocolAdapter {

    private final Environment env = Environment.getInstance();
    private final DatabaseConnectionProvider connectionProvider = new DatabaseConnectionProvider();
    private final List<PostgresNotificationListener> listeners = new ArrayList<>();
    private Engine engine;

    @Override
    public void start(Engine engine) {
        this.engine = engine;
        if (EventManagerOperatingMode.fromEnvironment().isLocal()) {
            System.out.println("PostgresNotificationAdapter disabled in local Event Manager mode");
            return;
        }
        if (!Boolean.parseBoolean(env.get("EA_POSTGRES_NOTIFICATIONS_ENABLED", "true"))) {
            System.out.println("PostgresNotificationAdapter disabled by EA_POSTGRES_NOTIFICATIONS_ENABLED=false");
            return;
        }

        for (ServiceCallDefinition def : engine.getRegistry().listDefinitions()) {
            if (!def.hasPostgresNotifications()) {
                continue;
            }
            for (ServiceCallDefinition.PostgresNotificationMeta meta : def.getPostgresNotifications()) {
                startListener(def.getServiceType(), meta);
            }
        }

        System.out.println("PostgresNotificationAdapter started. listeners=" + listeners.size());
    }

    @Override
    public void stop() {
        for (PostgresNotificationListener listener : listeners) {
            try {
                listener.close();
            } catch (Exception ignored) {
            }
        }
        listeners.clear();
    }

    private void startListener(String serviceType, ServiceCallDefinition.PostgresNotificationMeta meta) {
        PostgresNotificationConfig config = new PostgresNotificationConfig(
                meta.channel,
                duration("EA_POSTGRES_NOTIFICATION_POLL_TIMEOUT_MS", 500),
                duration("EA_POSTGRES_NOTIFICATION_RECONNECT_DELAY_MS", 5000));

        PostgresNotificationListener listener = new PostgresNotificationListener(
                config,
                () -> {
                    try {
                        return connectionProvider.getConnection(meta.database);
                    } catch (Exception e) {
                        throw new IllegalStateException("Failed to open Postgres notification connection", e);
                    }
                },
                message -> handleNotification(serviceType, message));
        listener.start();
        listeners.add(listener);
    }

    private void handleNotification(String serviceType, PostgresNotificationMessage message) {
        try {
            ServiceCall call = new ServiceCall(engine);
            call.setType(serviceType);
            call.setSource(ServiceCallSource.POSTGRES_NOTIFICATION);
            call.setParams(params(message));
            call.putMetadata("postgres.channel", message.channel());
            call.putMetadata("postgres.processId", message.processId());
            engine.execute(call);
        } catch (Exception e) {
            System.err.println("[PostgresNotificationAdapter] Handler error for serviceType="
                    + serviceType + ": " + safe(e.getMessage()));
        }
    }

    private JsonObject params(PostgresNotificationMessage message) {
        JsonObject params = new JsonObject();
        params.addProperty("channel", message.channel());
        params.addProperty("process_id", message.processId());

        String payload = message.payload();
        if (payload == null || payload.isBlank()) {
            params.add("payload", new JsonObject());
            return params;
        }

        try {
            JsonElement parsed = JsonParser.parseString(payload);
            params.add("payload", parsed == null || parsed.isJsonNull() ? new JsonObject() : parsed);
        } catch (Exception ignored) {
            params.addProperty("payload_text", payload);
        }
        return params;
    }

    private Duration duration(String key, long defaultMillis) {
        long millis = Long.parseLong(env.get(key, Long.toString(defaultMillis)));
        return Duration.ofMillis(Math.max(1, millis));
    }

    private String safe(String message) {
        return message == null || message.isBlank() ? "error" : message;
    }
}
