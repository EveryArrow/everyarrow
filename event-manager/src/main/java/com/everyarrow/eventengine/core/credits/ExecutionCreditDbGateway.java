package com.everyarrow.eventengine.core.credits;

import com.everyarrow.eventengine.core.services.runtime.PostgresHelper;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.UUID;

/**
 * Database boundary for finance.execution_credit_* functions.
 */
public class ExecutionCreditDbGateway {
    private static final String DATABASE = ExecutionCreditDataModel.DATABASE_NAME;
    private static final String SCHEMA = ExecutionCreditDataModel.SCHEMA_NAME;
    private static final String RESERVE_FUNCTION = "execution_credit_reserve";
    private static final String FINALIZE_FUNCTION = "execution_credit_finalize";
    private static final String STATUS_FUNCTION = "execution_credit_status";
    private static final String USAGE_SUMMARY_FUNCTION = "execution_usage_summary";
    private static final String LICENSE_IMPORT_FUNCTION = "execution_license_import";
    private static final String JSON_TYPE = "jsonb";

    private final DbFunctionCaller dbFunctionCaller;

    public ExecutionCreditDbGateway() {
        this(new PostgresHelper());
    }

    public ExecutionCreditDbGateway(PostgresHelper postgresHelper) {
        if (postgresHelper == null) {
            throw new IllegalArgumentException("postgresHelper is required");
        }
        this.dbFunctionCaller = postgresHelper::processDbFunction;
    }

    ExecutionCreditDbGateway(DbFunctionCaller dbFunctionCaller) {
        if (dbFunctionCaller == null) {
            throw new IllegalArgumentException("dbFunctionCaller is required");
        }
        this.dbFunctionCaller = dbFunctionCaller;
    }

    public CreditDecision reserve(ExecutionCreditRequest request, long creditsRequired) {
        JsonObject result = dbFunctionCaller.process(
                DATABASE,
                SCHEMA,
                RESERVE_FUNCTION,
                JSON_TYPE,
                reserveParams(request, creditsRequired));

        if (!booleanValue(result, "allowed")) {
            return CreditDecision.denied(
                    denialReason(result, "reason"),
                    longValue(result, "creditsRequired", creditsRequired),
                    longValue(result, "creditsAvailable", 0),
                    stringValue(result, "message"));
        }

        CreditReservation reservation = new CreditReservation(
                uuidValue(result, "reservationId"),
                request,
                longValue(result, "creditsReserved", creditsRequired),
                Instant.now());
        return CreditDecision.allowed(reservation);
    }

    public ExecutionUsageEvent finalizeUsage(CreditReservation reservation,
                                             ExecutionUsageFinalization finalization) {
        JsonObject result = dbFunctionCaller.process(
                DATABASE,
                SCHEMA,
                FINALIZE_FUNCTION,
                JSON_TYPE,
                finalizeParams(reservation, finalization));

        return new ExecutionUsageEvent(
                uuidValue(result, "usageEventId"),
                reservation,
                finalization,
                Instant.now());
    }

    public JsonObject status(JsonObject params) {
        return dbFunctionCaller.process(
                DATABASE,
                SCHEMA,
                STATUS_FUNCTION,
                JSON_TYPE,
                params == null ? new JsonObject() : params);
    }

    public JsonObject usageSummary(JsonObject params) {
        return dbFunctionCaller.process(
                DATABASE,
                SCHEMA,
                USAGE_SUMMARY_FUNCTION,
                JSON_TYPE,
                params == null ? new JsonObject() : params);
    }

    public JsonObject importLicense(JsonObject params) {
        return dbFunctionCaller.process(
                DATABASE,
                SCHEMA,
                LICENSE_IMPORT_FUNCTION,
                JSON_TYPE,
                params == null ? new JsonObject() : params);
    }

    private JsonObject reserveParams(ExecutionCreditRequest request, long creditsRequired) {
        JsonObject params = new JsonObject();
        params.addProperty("executionType", request.executionType().name());
        addIfNotBlank(params, "serviceType", request.serviceType());
        addIfNotBlank(params, "customerId", request.customerId());
        addIfNotBlank(params, "licenseId", request.licenseId());
        addIfNotBlank(params, "installationId", request.installationId());
        params.addProperty("creditsRequired", creditsRequired);
        return params;
    }

    private JsonObject finalizeParams(CreditReservation reservation,
                                      ExecutionUsageFinalization finalization) {
        JsonObject params = new JsonObject();
        params.addProperty("reservationId", reservation.reservationId().toString());
        params.addProperty("outcome", finalization.outcome().name());
        params.addProperty("creditsCharged", finalization.creditsCharged());
        params.addProperty("rowsRead", finalization.rowsRead());
        params.addProperty("rowsWritten", finalization.rowsWritten());
        addIfNotBlank(params, "message", finalization.message());
        return params;
    }

    private void addIfNotBlank(JsonObject params, String key, String value) {
        if (value != null && !value.isBlank()) {
            params.addProperty(key, value.trim());
        }
    }

    private boolean booleanValue(JsonObject result, String key) {
        return result != null && result.has(key) && !result.get(key).isJsonNull()
                && result.get(key).getAsBoolean();
    }

    private long longValue(JsonObject result, String key, long defaultValue) {
        if (result == null || !result.has(key) || result.get(key).isJsonNull()) {
            return defaultValue;
        }
        return result.get(key).getAsLong();
    }

    private String stringValue(JsonObject result, String key) {
        if (result == null || !result.has(key) || result.get(key).isJsonNull()) {
            return null;
        }
        String value = result.get(key).getAsString();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private UUID uuidValue(JsonObject result, String key) {
        String value = stringValue(result, key);
        if (value == null) {
            throw new IllegalArgumentException(key + " is required");
        }
        return UUID.fromString(value);
    }

    private CreditDenialReason denialReason(JsonObject result, String key) {
        String value = stringValue(result, key);
        if (value == null) {
            return CreditDenialReason.LEDGER_UNAVAILABLE;
        }
        return CreditDenialReason.valueOf(value);
    }

    interface DbFunctionCaller {
        JsonObject process(String database,
                           String schema,
                           String function,
                           String jsonType,
                           JsonObject params);
    }
}
