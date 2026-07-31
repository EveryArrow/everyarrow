package com.everyarrow.eventengine.core.credits;

import com.google.gson.JsonObject;

/**
 * Verified local execution license token payload.
 */
public record VerifiedExecutionLicense(
        String licenseId,
        String licenseType,
        String customerId,
        String installationId,
        long totalCredits,
        String validFrom,
        String validUntil,
        String issuedAt,
        String keyId,
        JsonObject metadata) {

    public VerifiedExecutionLicense {
        metadata = metadata == null ? new JsonObject() : metadata;
    }

    public JsonObject toLedgerParams() {
        JsonObject params = new JsonObject();
        params.addProperty("licenseId", licenseId);
        params.addProperty("licenseType", licenseType);
        params.addProperty("customerId", customerId);
        params.addProperty("installationId", installationId);
        params.addProperty("totalCredits", totalCredits);
        params.addProperty("validFrom", validFrom);
        if (validUntil != null) {
            params.addProperty("validUntil", validUntil);
        }
        params.addProperty("issuedAt", issuedAt);
        if (keyId != null) {
            params.addProperty("keyId", keyId);
        }
        params.add("metadata", metadata.deepCopy());
        return params;
    }
}
