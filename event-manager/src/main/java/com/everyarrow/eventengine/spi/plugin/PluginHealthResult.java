/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.spi.plugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Structured plugin readiness result.
 */
public class PluginHealthResult {

    public enum Status {
        UP,
        WARN,
        DOWN
    }

    public static final class Check {
        private final String name;
        private final Status status;
        private final String message;

        public Check(String name, Status status, String message) {
            this.name = name;
            this.status = status == null ? Status.DOWN : status;
            this.message = message;
        }

        public String getName() {
            return name;
        }

        public Status getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }

        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("name", name);
            json.addProperty("status", status.name());
            if (message != null && !message.isBlank()) {
                json.addProperty("message", message);
            }
            return json;
        }
    }

    private final String pluginId;
    private final Status status;
    private final List<Check> checks;
    private final boolean restartRequired;

    public PluginHealthResult(String pluginId, Status status, List<Check> checks, boolean restartRequired) {
        this.pluginId = pluginId;
        this.status = status == null ? Status.DOWN : status;
        this.checks = checks == null ? List.of() : List.copyOf(checks);
        this.restartRequired = restartRequired;
    }

    public static Builder builder(String pluginId) {
        return new Builder(pluginId);
    }

    public static PluginHealthResult up(String pluginId) {
        return builder(pluginId).up("plugin", "Plugin is healthy").build();
    }

    public static PluginHealthResult down(String pluginId, String checkName, String message) {
        return builder(pluginId).down(checkName, message).build();
    }

    public String getPluginId() {
        return pluginId;
    }

    public Status getStatus() {
        return status;
    }

    public List<Check> getChecks() {
        return Collections.unmodifiableList(checks);
    }

    public boolean isRestartRequired() {
        return restartRequired;
    }

    public boolean isDown() {
        return status == Status.DOWN;
    }

    public PluginHealthResult withPluginId(String pluginId) {
        return new PluginHealthResult(pluginId, status, checks, restartRequired);
    }

    public PluginHealthResult withRestartRequired(String checkName, String message) {
        Builder builder = builder(pluginId);
        for (Check check : checks) {
            builder.check(check.getName(), check.getStatus(), check.getMessage());
        }
        builder.warn(checkName, message);
        builder.restartRequired(true);
        return builder.build();
    }

    public PluginHealthResult merge(PluginHealthResult other) {
        if (other == null) {
            return this;
        }

        Builder builder = builder(pluginId == null || pluginId.isBlank() ? other.pluginId : pluginId);
        for (Check check : checks) {
            builder.check(check.getName(), check.getStatus(), check.getMessage());
        }
        for (Check check : other.checks) {
            builder.check(check.getName(), check.getStatus(), check.getMessage());
        }
        builder.restartRequired(restartRequired || other.restartRequired);
        return builder.build();
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("pluginId", pluginId);
        json.addProperty("status", status.name());
        json.addProperty("restartRequired", restartRequired);

        JsonArray checkArray = new JsonArray();
        for (Check check : checks) {
            checkArray.add(check.toJson());
        }
        json.add("checks", checkArray);
        return json;
    }

    public static final class Builder {
        private final String pluginId;
        private final List<Check> checks = new ArrayList<>();
        private boolean restartRequired;

        private Builder(String pluginId) {
            this.pluginId = pluginId;
        }

        public Builder up(String name, String message) {
            return check(name, Status.UP, message);
        }

        public Builder warn(String name, String message) {
            return check(name, Status.WARN, message);
        }

        public Builder down(String name, String message) {
            return check(name, Status.DOWN, message);
        }

        public Builder check(String name, Status status, String message) {
            checks.add(new Check(name, status, message));
            return this;
        }

        public Builder restartRequired(boolean restartRequired) {
            this.restartRequired = restartRequired;
            return this;
        }

        public PluginHealthResult build() {
            Status aggregate = Status.UP;
            for (Check check : checks) {
                if (check.getStatus() == Status.DOWN) {
                    aggregate = Status.DOWN;
                    break;
                }
                if (check.getStatus() == Status.WARN) {
                    aggregate = Status.WARN;
                }
            }
            return new PluginHealthResult(pluginId, aggregate, checks, restartRequired);
        }
    }
}
