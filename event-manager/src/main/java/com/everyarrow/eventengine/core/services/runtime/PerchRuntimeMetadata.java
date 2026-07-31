package com.everyarrow.eventengine.core.services.runtime;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.function.Function;

/**
 * Normalizes transport-specific Perch execution context into ServiceCall metadata.
 */
public final class PerchRuntimeMetadata {

    public static final String JOB_GROUP_EXECUTION_INSTANCE_ID = "perch.jobGroupExecutionInstanceId";
    public static final String JOB_EXECUTION_INSTANCE_ID = "perch.jobExecutionInstanceId";
    public static final String TASK_EXECUTION_INSTANCE_ID = "perch.taskExecutionInstanceId";
    public static final String DATABASE_CORRELATION_COLUMN = "perch_execution_id";
    public static final String DATABASE_CORRELATION_SCOPE = "most-specific-perch-execution";

    public static final String QUEUE_JOB_GROUP_EXECUTION_INSTANCE_ID = "ea_perch_job_group_execution_instance_id";
    public static final String QUEUE_JOB_EXECUTION_INSTANCE_ID = "ea_perch_job_execution_instance_id";
    public static final String QUEUE_TASK_EXECUTION_INSTANCE_ID = "ea_perch_task_execution_instance_id";

    private PerchRuntimeMetadata() {
    }

    public static void normalizeFromParams(ServiceCall call) {
        if (call == null) {
            return;
        }
        copyFromPayload(call, call.getParams());
    }

    public static void copyFromPayload(ServiceCall call, JsonObject payload) {
        if (call == null || payload == null || !payload.has("perch") || !payload.get("perch").isJsonObject()) {
            return;
        }

        JsonObject perch = payload.getAsJsonObject("perch");
        putIfPresent(call, JOB_GROUP_EXECUTION_INSTANCE_ID,
                text(perch, "jobGroupExecutionInstanceId", "job_group_execution_instance_id"));
        putIfPresent(call, JOB_EXECUTION_INSTANCE_ID,
                text(perch, "jobExecutionInstanceId", "job_execution_instance_id"));
        putIfPresent(call, TASK_EXECUTION_INSTANCE_ID,
                text(perch, "taskExecutionInstanceId", "task_execution_instance_id"));
    }

    public static void normalizeFromQueueAttributes(ServiceCall call, Map<String, String> attributes) {
        if (call == null || attributes == null || attributes.isEmpty()) {
            return;
        }

        putIfPresent(call, JOB_GROUP_EXECUTION_INSTANCE_ID,
                firstText(attributes, QUEUE_JOB_GROUP_EXECUTION_INSTANCE_ID, "perch.jobGroupExecutionInstanceId"));
        putIfPresent(call, JOB_EXECUTION_INSTANCE_ID,
                firstText(attributes, QUEUE_JOB_EXECUTION_INSTANCE_ID, "perch.jobExecutionInstanceId"));
        putIfPresent(call, TASK_EXECUTION_INSTANCE_ID,
                firstText(attributes, QUEUE_TASK_EXECUTION_INSTANCE_ID, "perch.taskExecutionInstanceId"));
    }

    public static void normalizeFromHttpHeaders(ServiceCall call, Function<String, String> headerLookup) {
        if (call == null || headerLookup == null) {
            return;
        }

        putIfPresent(call, JOB_GROUP_EXECUTION_INSTANCE_ID,
                firstHeader(headerLookup, QUEUE_JOB_GROUP_EXECUTION_INSTANCE_ID, "perch.jobGroupExecutionInstanceId"));
        putIfPresent(call, JOB_EXECUTION_INSTANCE_ID,
                firstHeader(headerLookup, QUEUE_JOB_EXECUTION_INSTANCE_ID, "perch.jobExecutionInstanceId"));
        putIfPresent(call, TASK_EXECUTION_INSTANCE_ID,
                firstHeader(headerLookup, QUEUE_TASK_EXECUTION_INSTANCE_ID, "perch.taskExecutionInstanceId"));
    }

    public static JsonObject toJson(ServiceCall call) {
        JsonObject perch = new JsonObject();
        addIfPresent(perch, "jobGroupExecutionInstanceId", stringValue(call, JOB_GROUP_EXECUTION_INSTANCE_ID));
        addIfPresent(perch, "jobExecutionInstanceId", stringValue(call, JOB_EXECUTION_INSTANCE_ID));
        addIfPresent(perch, "taskExecutionInstanceId", stringValue(call, TASK_EXECUTION_INSTANCE_ID));
        return perch;
    }

    /**
     * Returns the narrowest active Perch execution for persisted-record correlation.
     */
    public static String mostSpecificExecutionId(ServiceCall call) {
        String taskExecutionId = stringValue(call, TASK_EXECUTION_INSTANCE_ID);
        if (taskExecutionId != null) {
            return taskExecutionId;
        }
        String jobExecutionId = stringValue(call, JOB_EXECUTION_INSTANCE_ID);
        if (jobExecutionId != null) {
            return jobExecutionId;
        }
        return stringValue(call, JOB_GROUP_EXECUTION_INSTANCE_ID);
    }

    private static String text(JsonObject json, String... keys) {
        if (json == null || keys == null) {
            return null;
        }

        for (String key : keys) {
            if (key == null || !json.has(key)) {
                continue;
            }
            JsonElement value = json.get(key);
            if (value == null || value.isJsonNull()) {
                continue;
            }
            try {
                String asString = value.getAsString();
                if (asString != null && !asString.isBlank()) {
                    return asString.trim();
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static String firstText(Map<String, String> attributes, String... keys) {
        for (String key : keys) {
            String value = firstMapValue(attributes, key);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String firstHeader(Function<String, String> headerLookup, String... names) {
        for (String name : names) {
            String value = headerLookup.apply(name);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String firstMapValue(Map<String, String> values, String key) {
        String direct = values.get(key);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static void putIfPresent(ServiceCall call, String key, String value) {
        if (value != null && !value.isBlank()) {
            call.putMetadata(key, value.trim());
        }
    }

    private static void addIfPresent(JsonObject json, String key, String value) {
        if (value != null && !value.isBlank()) {
            json.addProperty(key, value);
        }
    }

    private static String stringValue(ServiceCall call, String key) {
        if (call == null) {
            return null;
        }
        Object value = call.getMetadata(key);
        if (value == null) {
            return null;
        }
        String asString = value.toString();
        return asString.isBlank() ? null : asString;
    }
}
