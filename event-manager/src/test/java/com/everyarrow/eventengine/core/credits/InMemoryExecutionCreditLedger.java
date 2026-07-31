package com.everyarrow.eventengine.core.credits;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Deterministic test implementation of the finance execution-credit function contract.
 */
final class InMemoryExecutionCreditLedger implements ExecutionCreditDbGateway.DbFunctionCaller {
    private final Map<UUID, LicenseState> licenses = new LinkedHashMap<>();
    private final Map<UUID, ReservationState> reservations = new LinkedHashMap<>();
    private final List<UsageState> usageEvents = new ArrayList<>();

    ExecutionCreditDbGateway gateway() {
        return new ExecutionCreditDbGateway(this);
    }

    @Override
    public JsonObject process(String database,
                              String schema,
                              String function,
                              String jsonType,
                              JsonObject params) {
        if (!ExecutionCreditDataModel.DATABASE_NAME.equals(database)
                || !ExecutionCreditDataModel.SCHEMA_NAME.equals(schema)
                || !"jsonb".equals(jsonType)) {
            throw new IllegalArgumentException("Unexpected execution-credit database contract");
        }

        JsonObject request = params == null ? new JsonObject() : params;
        return switch (function) {
            case "execution_credit_reserve" -> reserve(request);
            case "execution_credit_finalize" -> finalizeUsage(request);
            case "execution_credit_status" -> status(request);
            case "execution_usage_summary" -> usageSummary(request);
            case "execution_license_import" -> importLicense(request);
            default -> throw new IllegalArgumentException("Unsupported finance function: " + function);
        };
    }

    private JsonObject importLicense(JsonObject params) {
        UUID licenseId = uuid(params, "licenseId");
        LicenseState state = licenses.computeIfAbsent(licenseId, ignored -> new LicenseState(licenseId));
        state.licenseType = requiredString(params, "licenseType");
        state.status = "active";
        state.customerId = requiredString(params, "customerId");
        state.installationId = requiredString(params, "installationId");
        state.totalCredits = requiredLong(params, "totalCredits");

        JsonObject result = new JsonObject();
        result.addProperty("imported", true);
        result.addProperty("licenseId", state.id.toString());
        result.addProperty("licenseType", state.licenseType);
        result.addProperty("status", state.status);
        result.addProperty("customerId", state.customerId);
        result.addProperty("installationId", state.installationId);
        result.add("credits", credits(state));
        return result;
    }

    private JsonObject reserve(JsonObject params) {
        long creditsRequired = requiredLong(params, "creditsRequired");
        LicenseState state = licenseFor(params);
        if (state == null || !"active".equals(state.status)) {
            return denied(CreditDenialReason.LICENSE_REQUIRED, creditsRequired, 0,
                    "An active execution license is required");
        }

        long available = available(state);
        if (available < creditsRequired) {
            return denied(CreditDenialReason.CREDITS_EXHAUSTED, creditsRequired, available,
                    "Execution credits are exhausted");
        }

        UUID reservationId = UUID.randomUUID();
        state.reserved += creditsRequired;
        reservations.put(reservationId, new ReservationState(
                reservationId,
                state.id,
                string(params, "executionType"),
                string(params, "serviceType"),
                creditsRequired));

        JsonObject result = new JsonObject();
        result.addProperty("allowed", true);
        result.addProperty("reservationId", reservationId.toString());
        result.addProperty("creditsReserved", creditsRequired);
        result.addProperty("creditsRequired", creditsRequired);
        result.addProperty("creditsAvailable", available(state));
        return result;
    }

    private JsonObject finalizeUsage(JsonObject params) {
        UUID reservationId = uuid(params, "reservationId");
        ReservationState reservation = reservations.get(reservationId);
        if (reservation == null || reservation.finalized) {
            throw new IllegalArgumentException("Unknown or finalized reservation: " + reservationId);
        }

        LicenseState license = licenses.get(reservation.licenseId);
        long charged = requiredLong(params, "creditsCharged");
        license.reserved -= reservation.creditsReserved;
        license.consumed += charged;
        reservation.finalized = true;

        UUID usageEventId = UUID.randomUUID();
        usageEvents.add(new UsageState(
                usageEventId,
                license.id,
                reservation.executionType,
                reservation.serviceType,
                string(params, "outcome"),
                charged,
                requiredLong(params, "rowsRead"),
                requiredLong(params, "rowsWritten")));

        JsonObject result = new JsonObject();
        result.addProperty("usageEventId", usageEventId.toString());
        return result;
    }

    private JsonObject status(JsonObject params) {
        LicenseState state = licenseFor(params);
        JsonObject result = new JsonObject();
        result.addProperty("ledgerAvailable", true);
        result.addProperty("enforcementActive", true);

        if (state == null) {
            result.addProperty("reason", CreditDenialReason.LICENSE_REQUIRED.name());
            result.add("license", new JsonObject());
            result.add("credits", emptyCredits());
            result.add("upgrade", upgrade(true, CreditDenialReason.LICENSE_REQUIRED.name()));
            return result;
        }

        JsonObject license = new JsonObject();
        license.addProperty("id", state.id.toString());
        license.addProperty("licenseType", state.licenseType);
        license.addProperty("status", state.status);
        license.addProperty("customerId", state.customerId);
        license.addProperty("installationId", state.installationId);
        result.add("license", license);
        result.add("credits", credits(state));
        result.add("upgrade", upgrade(false, null));
        return result;
    }

    private JsonObject usageSummary(JsonObject params) {
        LicenseState state = licenseFor(params);
        UUID licenseId = state == null ? optionalUuid(params, "licenseId") : state.id;
        List<UsageState> matching = usageEvents.stream()
                .filter(event -> licenseId == null || licenseId.equals(event.licenseId))
                .toList();

        JsonObject result = new JsonObject();
        if (licenseId != null) {
            result.addProperty("licenseId", licenseId.toString());
        }
        result.addProperty("totalEvents", matching.size());
        result.addProperty("creditsCharged", sum(matching, UsageState::creditsCharged));
        result.addProperty("rowsRead", sum(matching, UsageState::rowsRead));
        result.addProperty("rowsWritten", sum(matching, UsageState::rowsWritten));
        result.addProperty("succeeded", matching.stream().filter(event -> "SUCCEEDED".equals(event.outcome)).count());
        result.addProperty("failed", matching.stream().filter(event -> "FAILED".equals(event.outcome)).count());
        result.add("byExecutionType", groups(matching, true));
        result.add("byServiceType", groups(matching, false));
        return result;
    }

    private JsonArray groups(List<UsageState> events, boolean byExecutionType) {
        Map<String, List<UsageState>> grouped = new LinkedHashMap<>();
        for (UsageState event : events) {
            String key = byExecutionType ? event.executionType : event.serviceType;
            if (key != null) {
                grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(event);
            }
        }

        JsonArray result = new JsonArray();
        grouped.forEach((key, groupEvents) -> {
            JsonObject group = new JsonObject();
            group.addProperty("key", key);
            group.addProperty(byExecutionType ? "executionType" : "serviceType", key);
            group.addProperty("eventCount", groupEvents.size());
            group.addProperty("creditsCharged", sum(groupEvents, UsageState::creditsCharged));
            group.addProperty("rowsRead", sum(groupEvents, UsageState::rowsRead));
            group.addProperty("rowsWritten", sum(groupEvents, UsageState::rowsWritten));
            result.add(group);
        });
        return result;
    }

    private long sum(List<UsageState> events, LongValue value) {
        long total = 0;
        for (UsageState event : events) {
            total += value.get(event);
        }
        return total;
    }

    private LicenseState licenseFor(JsonObject params) {
        UUID licenseId = optionalUuid(params, "licenseId");
        if (licenseId != null) {
            return matchingIdentity(licenses.get(licenseId), params);
        }
        for (LicenseState state : licenses.values()) {
            if (matchingIdentity(state, params) != null) {
                return state;
            }
        }
        return null;
    }

    private LicenseState matchingIdentity(LicenseState state, JsonObject params) {
        if (state == null) {
            return null;
        }
        String customerId = string(params, "customerId");
        String installationId = string(params, "installationId");
        if (customerId != null && !customerId.equals(state.customerId)) {
            return null;
        }
        if (installationId != null && !installationId.equals(state.installationId)) {
            return null;
        }
        return state;
    }

    private JsonObject denied(CreditDenialReason reason,
                              long creditsRequired,
                              long creditsAvailable,
                              String message) {
        JsonObject result = new JsonObject();
        result.addProperty("allowed", false);
        result.addProperty("reason", reason.name());
        result.addProperty("creditsRequired", creditsRequired);
        result.addProperty("creditsAvailable", creditsAvailable);
        result.addProperty("message", message);
        return result;
    }

    private JsonObject credits(LicenseState state) {
        JsonObject credits = new JsonObject();
        credits.addProperty("total", state.totalCredits);
        credits.addProperty("reserved", state.reserved);
        credits.addProperty("consumed", state.consumed);
        credits.addProperty("available", available(state));
        return credits;
    }

    private JsonObject emptyCredits() {
        JsonObject credits = new JsonObject();
        credits.addProperty("total", 0);
        credits.addProperty("reserved", 0);
        credits.addProperty("consumed", 0);
        credits.addProperty("available", 0);
        return credits;
    }

    private JsonObject upgrade(boolean required, String reason) {
        JsonObject upgrade = new JsonObject();
        upgrade.addProperty("required", required);
        upgrade.addProperty("action", "contact_everyarrow");
        if (reason != null) {
            upgrade.addProperty("reason", reason);
        }
        return upgrade;
    }

    private long available(LicenseState state) {
        return Math.max(0, state.totalCredits - state.reserved - state.consumed);
    }

    private UUID uuid(JsonObject params, String key) {
        return UUID.fromString(requiredString(params, key));
    }

    private UUID optionalUuid(JsonObject params, String key) {
        String value = string(params, key);
        return value == null ? null : UUID.fromString(value);
    }

    private long requiredLong(JsonObject params, String key) {
        if (params == null || !params.has(key) || params.get(key).isJsonNull()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return params.get(key).getAsLong();
    }

    private String requiredString(JsonObject params, String key) {
        String value = string(params, key);
        if (value == null) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }

    private String string(JsonObject params, String key) {
        if (params == null || !params.has(key) || params.get(key).isJsonNull()) {
            return null;
        }
        String value = params.get(key).getAsString();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static final class LicenseState {
        private final UUID id;
        private String licenseType;
        private String status;
        private String customerId;
        private String installationId;
        private long totalCredits;
        private long reserved;
        private long consumed;

        private LicenseState(UUID id) {
            this.id = id;
        }
    }

    private static final class ReservationState {
        private final UUID id;
        private final UUID licenseId;
        private final String executionType;
        private final String serviceType;
        private final long creditsReserved;
        private boolean finalized;

        private ReservationState(UUID id,
                                 UUID licenseId,
                                 String executionType,
                                 String serviceType,
                                 long creditsReserved) {
            this.id = id;
            this.licenseId = licenseId;
            this.executionType = executionType;
            this.serviceType = serviceType;
            this.creditsReserved = creditsReserved;
        }
    }

    private record UsageState(
            UUID id,
            UUID licenseId,
            String executionType,
            String serviceType,
            String outcome,
            long creditsCharged,
            long rowsRead,
            long rowsWritten) {
    }

    @FunctionalInterface
    private interface LongValue {
        long get(UsageState event);
    }
}
