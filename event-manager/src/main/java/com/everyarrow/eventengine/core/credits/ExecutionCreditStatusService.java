package com.everyarrow.eventengine.core.credits;

import com.everyarrow.eventengine.core.services.runtime.ServiceCall;
import com.everyarrow.utilities.environment.Environment;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Locale;

/**
 * Read-only console-facing view of execution-credit mode, balance, and usage.
 */
public class ExecutionCreditStatusService {

    private final Environment environment;
    private final ExecutionCreditDbGateway dbGateway;
    private final ExecutionCreditContextResolver contextResolver;

    public ExecutionCreditStatusService() {
        this(Environment.getInstance(), new ExecutionCreditDbGateway(), new ExecutionCreditContextResolver());
    }

    ExecutionCreditStatusService(Environment environment,
                                 ExecutionCreditDbGateway dbGateway,
                                 ExecutionCreditContextResolver contextResolver) {
        this.environment = environment == null ? Environment.getInstance() : environment;
        if (dbGateway == null) {
            throw new IllegalArgumentException("dbGateway is required");
        }
        this.dbGateway = dbGateway;
        this.contextResolver = contextResolver == null
                ? new ExecutionCreditContextResolver(this.environment)
                : contextResolver;
    }

    public JsonObject status(ServiceCall call) {
        String mode = mode();
        if (ExecutionCreditGateFactory.MODE_ALLOWING.equals(mode)) {
            return allowingStatus();
        }

        if (!ExecutionCreditGateFactory.MODE_LOCAL.equals(mode)) {
            return ledgerUnavailable(mode, "Unsupported execution credit mode", true);
        }

        try {
            return withStatusDefaults(dbGateway.status(baseLedgerParams(call)), mode);
        } catch (Exception ex) {
            return ledgerUnavailable(mode, "Execution credit ledger is unavailable", true);
        }
    }

    public JsonObject usageSummary(ServiceCall call) {
        String mode = mode();
        if (ExecutionCreditGateFactory.MODE_ALLOWING.equals(mode)) {
            return emptyUsage(mode, true, false, null, call);
        }

        if (!ExecutionCreditGateFactory.MODE_LOCAL.equals(mode)) {
            return emptyUsage(mode, false, true, "Unsupported execution credit mode", call);
        }

        try {
            return withUsageDefaults(dbGateway.usageSummary(usageLedgerParams(call)), mode);
        } catch (Exception ex) {
            return emptyUsage(mode, false, true, "Execution credit ledger is unavailable", call);
        }
    }

    private JsonObject baseLedgerParams(ServiceCall call) {
        return contextResolver.contextFor(call).toLedgerParams();
    }

    private JsonObject usageLedgerParams(ServiceCall call) {
        JsonObject params = baseLedgerParams(call);
        addParamIfPresent(call, params, "from");
        addParamIfPresent(call, params, "to");
        return params;
    }

    private JsonObject allowingStatus() {
        JsonObject status = new JsonObject();
        status.addProperty("mode", ExecutionCreditGateFactory.MODE_ALLOWING);
        status.addProperty("ledgerAvailable", true);
        status.addProperty("enforcementActive", false);
        status.add("license", new JsonObject());
        status.add("credits", credits(0, 0, 0, 0));
        status.add("upgrade", upgrade(false, null, "Execution credit enforcement is in allowing mode"));
        return status;
    }

    private JsonObject ledgerUnavailable(String mode, String message, boolean enforcementActive) {
        JsonObject status = new JsonObject();
        status.addProperty("mode", mode);
        status.addProperty("ledgerAvailable", false);
        status.addProperty("enforcementActive", enforcementActive);
        status.addProperty("reason", CreditDenialReason.LEDGER_UNAVAILABLE.name());
        status.addProperty("message", message);
        status.add("license", new JsonObject());
        status.add("credits", credits(0, 0, 0, 0));
        status.add("upgrade", upgrade(false, CreditDenialReason.LEDGER_UNAVAILABLE.name(), message));
        return status;
    }

    private JsonObject emptyUsage(String mode,
                                  boolean ledgerAvailable,
                                  boolean ledgerUnavailable,
                                  String message,
                                  ServiceCall call) {
        JsonObject usage = new JsonObject();
        usage.addProperty("mode", mode);
        usage.addProperty("ledgerAvailable", ledgerAvailable);
        usage.addProperty("enforcementActive", ExecutionCreditGateFactory.MODE_LOCAL.equals(mode));
        ExecutionCreditContext context = contextResolver.contextFor(call);
        addOptional(usage, "licenseId", context.licenseId());
        addOptional(usage, "from", paramString(call, "from"));
        addOptional(usage, "to", paramString(call, "to"));
        if (ledgerUnavailable) {
            usage.addProperty("reason", CreditDenialReason.LEDGER_UNAVAILABLE.name());
            usage.addProperty("message", message == null || message.isBlank()
                    ? "Execution credit ledger is unavailable"
                    : message);
        }
        usage.addProperty("totalEvents", 0);
        usage.addProperty("creditsCharged", 0);
        usage.addProperty("rowsRead", 0);
        usage.addProperty("rowsWritten", 0);
        usage.addProperty("succeeded", 0);
        usage.addProperty("failed", 0);
        usage.add("byExecutionType", new JsonArray());
        usage.add("byServiceType", new JsonArray());
        return usage;
    }

    private JsonObject withStatusDefaults(JsonObject result, String mode) {
        JsonObject status = result == null ? new JsonObject() : result;
        addDefault(status, "mode", mode);
        addDefault(status, "ledgerAvailable", true);
        addDefault(status, "enforcementActive", true);
        addObjectDefault(status, "license");
        if (!status.has("credits") || !status.get("credits").isJsonObject()) {
            status.add("credits", credits(
                    longValue(status, "totalCredits"),
                    longValue(status, "creditsReserved"),
                    longValue(status, "creditsConsumed"),
                    longValue(status, "creditsAvailable")));
        }
        addObjectDefault(status, "upgrade");
        return status;
    }

    private JsonObject withUsageDefaults(JsonObject result, String mode) {
        JsonObject usage = result == null ? new JsonObject() : result;
        addDefault(usage, "mode", mode);
        addDefault(usage, "ledgerAvailable", true);
        addDefault(usage, "enforcementActive", true);
        addDefault(usage, "totalEvents", 0);
        addDefault(usage, "creditsCharged", 0);
        addDefault(usage, "rowsRead", 0);
        addDefault(usage, "rowsWritten", 0);
        addDefault(usage, "succeeded", 0);
        addDefault(usage, "failed", 0);
        addArrayDefault(usage, "byExecutionType");
        addArrayDefault(usage, "byServiceType");
        return usage;
    }

    private JsonObject credits(long total, long reserved, long consumed, long available) {
        JsonObject credits = new JsonObject();
        credits.addProperty("total", total);
        credits.addProperty("reserved", reserved);
        credits.addProperty("consumed", consumed);
        credits.addProperty("available", available);
        return credits;
    }

    private JsonObject upgrade(boolean required, String reason, String message) {
        JsonObject upgrade = new JsonObject();
        upgrade.addProperty("required", required);
        upgrade.addProperty("action", "contact_everyarrow");
        upgrade.addProperty("localUpgradeAvailable", true);
        upgrade.addProperty("cloudUpgradeAvailable", true);
        if (reason != null && !reason.isBlank()) {
            upgrade.addProperty("reason", reason);
        }
        if (message != null && !message.isBlank()) {
            upgrade.addProperty("message", message);
        }
        return upgrade;
    }

    private void addParamIfPresent(ServiceCall call, JsonObject params, String key) {
        String value = paramString(call, key);
        if (value != null) {
            params.addProperty(key, value);
        }
    }

    private void addOptional(JsonObject object, String key, String value) {
        if (value != null) {
            object.addProperty(key, value);
        }
    }

    private String paramString(ServiceCall call, String key) {
        if (call == null || call.getParams() == null || key == null
                || !call.getParams().has(key) || call.getParams().get(key).isJsonNull()) {
            return null;
        }
        try {
            String value = call.getParams().get(key).getAsString();
            return value == null || value.isBlank() ? null : value.trim();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String mode() {
        String configured = environment.get(ExecutionCreditGateFactory.MODE_CONFIG, ExecutionCreditGateFactory.MODE_ALLOWING);
        return configured == null || configured.isBlank()
                ? ExecutionCreditGateFactory.MODE_ALLOWING
                : configured.trim().toLowerCase(Locale.ROOT);
    }

    private void addDefault(JsonObject object, String key, String value) {
        if (!object.has(key)) {
            object.addProperty(key, value);
        }
    }

    private void addDefault(JsonObject object, String key, boolean value) {
        if (!object.has(key)) {
            object.addProperty(key, value);
        }
    }

    private void addDefault(JsonObject object, String key, long value) {
        if (!object.has(key)) {
            object.addProperty(key, value);
        }
    }

    private void addObjectDefault(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonObject()) {
            object.add(key, new JsonObject());
        }
    }

    private void addArrayDefault(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonArray()) {
            object.add(key, new JsonArray());
        }
    }

    private long longValue(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return 0;
        }
        return object.get(key).getAsLong();
    }
}
