package com.everyarrow.eventengine.spi.model;

import com.google.gson.annotations.SerializedName;

/**
 * Immutable representation of a Postgres-discovered JSON function.
 *
 * These are discovered at developer-time or CI-time, not engine runtime,
 * and later promoted into ServiceCallDefinition objects. JSON-authored registry
 * entries may also attach optional annotation metadata for security and
 * monitoring.
 */
public record DbDiscoveredService(
                @SerializedName(value = "database", alternate = {"db_name"}) String database,
                @SerializedName(value = "schema", alternate = {"schema_name"}) String schema,
                @SerializedName(value = "function", alternate = {"function_name"}) String function,
                @SerializedName(value = "serviceType", alternate = {"service_type"}) String serviceType,
                @SerializedName(value = "httpMethod", alternate = {"http_method"}) String httpMethod,
                @SerializedName(value = "routePath", alternate = {"route_path", "route_path_final"}) String routePath,
                String description,
                String definition,
                @SerializedName(value = "jsonType", alternate = {"json_type"}) String jsonType,
                ServiceCallDefinition.Annotations annotations) {
}
