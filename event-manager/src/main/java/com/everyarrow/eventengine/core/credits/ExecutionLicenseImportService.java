package com.everyarrow.eventengine.core.credits;

import com.everyarrow.eventengine.core.services.runtime.ServiceCall;
import com.google.gson.JsonObject;

import java.time.Instant;

/**
 * Imports locally verified execution-license tokens into the finance ledger.
 */
public class ExecutionLicenseImportService {
    private final ExecutionLicenseTokenVerifier tokenVerifier;
    private final ExecutionCreditDbGateway dbGateway;
    private final ExecutionCreditContextResolver contextResolver;

    public ExecutionLicenseImportService() {
        this(new ExecutionLicenseTokenVerifier(), new ExecutionCreditDbGateway(), new ExecutionCreditContextResolver());
    }

    ExecutionLicenseImportService(ExecutionLicenseTokenVerifier tokenVerifier,
                                  ExecutionCreditDbGateway dbGateway,
                                  ExecutionCreditContextResolver contextResolver) {
        if (tokenVerifier == null) {
            throw new IllegalArgumentException("tokenVerifier is required");
        }
        if (dbGateway == null) {
            throw new IllegalArgumentException("dbGateway is required");
        }
        this.tokenVerifier = tokenVerifier;
        this.dbGateway = dbGateway;
        this.contextResolver = contextResolver == null
                ? new ExecutionCreditContextResolver()
                : contextResolver;
    }

    public JsonObject importLicense(ServiceCall call) {
        try {
            VerifiedExecutionLicense license = tokenVerifier.verify(licenseToken(call));
            requireInstallationMatch(call, license);
            JsonObject result = dbGateway.importLicense(ledgerParams(license, call));
            return withImportDefaults(result, license);
        } catch (ExecutionLicenseImportException ex) {
            return failure(ex.reason(), ex.getMessage());
        } catch (Exception ex) {
            return failure(ExecutionLicenseImportFailureReason.LEDGER_UNAVAILABLE,
                    "Execution license ledger is unavailable");
        }
    }

    private JsonObject ledgerParams(VerifiedExecutionLicense license, ServiceCall call) {
        JsonObject params = license.toLedgerParams();
        params.addProperty("verifiedAt", Instant.now().toString());
        ExecutionCreditContext context = contextResolver.contextFor(call);
        addIfPresent(params, "contextCustomerId", context.customerId());
        addIfPresent(params, "contextLicenseId", context.licenseId());
        addIfPresent(params, "contextInstallationId", context.installationId());
        return params;
    }

    private void requireInstallationMatch(ServiceCall call, VerifiedExecutionLicense license) {
        String expectedInstallationId = contextResolver.contextFor(call).installationId();
        if (expectedInstallationId == null || expectedInstallationId.equals(license.installationId())) {
            return;
        }
        throw new ExecutionLicenseImportException(
                ExecutionLicenseImportFailureReason.INSTALLATION_MISMATCH,
                "License token installation does not match this runtime");
    }

    private JsonObject withImportDefaults(JsonObject result, VerifiedExecutionLicense license) {
        JsonObject response = result == null ? new JsonObject() : result;
        addDefault(response, "imported", false);
        addDefault(response, "licenseId", license.licenseId());
        addDefault(response, "licenseType", license.licenseType());
        addDefault(response, "status", "active");
        addDefault(response, "customerId", license.customerId());
        addDefault(response, "installationId", license.installationId());
        if (!response.has("credits") || !response.get("credits").isJsonObject()) {
            response.add("credits", credits(license.totalCredits(), 0, 0, license.totalCredits()));
        }
        if (!response.has("statusPayload") || !response.get("statusPayload").isJsonObject()) {
            JsonObject statusPayload = new JsonObject();
            statusPayload.addProperty("mode", ExecutionCreditGateFactory.MODE_LOCAL);
            statusPayload.addProperty("ledgerAvailable", true);
            statusPayload.addProperty("enforcementActive", true);
            statusPayload.add("license", licenseObject(response));
            statusPayload.add("credits", response.getAsJsonObject("credits").deepCopy());
            statusPayload.add("upgrade", upgrade(false, null, null));
            response.add("statusPayload", statusPayload);
        }
        return response;
    }

    private JsonObject licenseObject(JsonObject response) {
        JsonObject license = new JsonObject();
        addIfPresent(license, "id", stringValue(response, "licenseId"));
        addIfPresent(license, "licenseType", stringValue(response, "licenseType"));
        addIfPresent(license, "status", stringValue(response, "status"));
        addIfPresent(license, "customerId", stringValue(response, "customerId"));
        addIfPresent(license, "installationId", stringValue(response, "installationId"));
        return license;
    }

    private JsonObject failure(ExecutionLicenseImportFailureReason reason, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("imported", false);
        response.addProperty("reason", reason.name());
        response.addProperty("message", message == null || message.isBlank()
                ? "Execution license import failed"
                : message);
        response.add("credits", credits(0, 0, 0, 0));
        JsonObject statusPayload = new JsonObject();
        statusPayload.addProperty("mode", ExecutionCreditGateFactory.MODE_LOCAL);
        statusPayload.addProperty("ledgerAvailable", reason != ExecutionLicenseImportFailureReason.LEDGER_UNAVAILABLE);
        statusPayload.addProperty("enforcementActive", true);
        statusPayload.addProperty("reason", reason == ExecutionLicenseImportFailureReason.LEDGER_UNAVAILABLE
                ? CreditDenialReason.LEDGER_UNAVAILABLE.name()
                : reason.name());
        statusPayload.addProperty("message", response.get("message").getAsString());
        statusPayload.add("license", new JsonObject());
        statusPayload.add("credits", credits(0, 0, 0, 0));
        statusPayload.add("upgrade", upgrade(false, statusPayload.get("reason").getAsString(),
                response.get("message").getAsString()));
        response.add("statusPayload", statusPayload);
        return response;
    }

    private String licenseToken(ServiceCall call) {
        String value = paramString(call, "licenseToken");
        return value == null ? paramString(call, "token") : value;
    }

    private String paramString(ServiceCall call, String key) {
        if (call == null || call.getParams() == null || key == null
                || !call.getParams().has(key) || call.getParams().get(key).isJsonNull()) {
            return null;
        }
        try {
            String value = call.getParams().get(key).getAsString();
            return value == null || value.isBlank() ? null : value.trim();
        } catch (Exception ex) {
            return null;
        }
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
        addIfPresent(upgrade, "reason", reason);
        addIfPresent(upgrade, "message", message);
        return upgrade;
    }

    private void addDefault(JsonObject object, String key, boolean value) {
        if (!object.has(key)) {
            object.addProperty(key, value);
        }
    }

    private void addDefault(JsonObject object, String key, String value) {
        if (!object.has(key) && value != null) {
            object.addProperty(key, value);
        }
    }

    private void addIfPresent(JsonObject object, String key, String value) {
        if (value != null && !value.isBlank()) {
            object.addProperty(key, value.trim());
        }
    }

    private String stringValue(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        try {
            String value = object.get(key).getAsString();
            return value == null || value.isBlank() ? null : value.trim();
        } catch (Exception ex) {
            return null;
        }
    }
}
