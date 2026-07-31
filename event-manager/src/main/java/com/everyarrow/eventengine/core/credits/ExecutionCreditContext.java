package com.everyarrow.eventengine.core.credits;

import com.google.gson.JsonObject;

/**
 * Identity fields used by local execution-credit ledger calls.
 */
public record ExecutionCreditContext(
        String customerId,
        String licenseId,
        String installationId) {

    public ExecutionCreditContext {
        customerId = normalize(customerId);
        licenseId = normalize(licenseId);
        installationId = normalize(installationId);
    }

    public JsonObject toLedgerParams() {
        JsonObject params = new JsonObject();
        addIfPresent(params, "customerId", customerId);
        addIfPresent(params, "licenseId", licenseId);
        addIfPresent(params, "installationId", installationId);
        return params;
    }

    private static void addIfPresent(JsonObject params, String key, String value) {
        if (value != null) {
            params.addProperty(key, value);
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
