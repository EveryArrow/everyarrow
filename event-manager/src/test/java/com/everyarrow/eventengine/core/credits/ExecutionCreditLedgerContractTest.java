package com.everyarrow.eventengine.core.credits;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionCreditLedgerContractTest {

    @Test
    void reserveFinalizeMissingLicenseAndExhaustedCreditsFollowLedgerContract() {
        InMemoryExecutionCreditLedger ledger = new InMemoryExecutionCreditLedger();
        ExecutionCreditDbGateway gateway = ledger.gateway();
        DbExecutionCreditGate gate = new DbExecutionCreditGate(new DefaultExecutionCreditPolicy(), gateway);

        UUID activeLicenseId = UUID.randomUUID();
        String installationId = "contract-installation-" + activeLicenseId;
        gateway.importLicense(licenseImportParams(
                activeLicenseId,
                "contract-customer",
                installationId,
                500));

        CreditDecision allowed = gate.reserve(ExecutionCreditRequest.billable(
                ExecutionType.REST_SERVICE_CALL,
                "contract.rest.execute",
                "contract-customer",
                activeLicenseId.toString(),
                installationId));

        assertTrue(allowed.allowed());
        assertEquals(1, allowed.reservation().creditsReserved());
        assertCredits(gateway, activeLicenseId, "contract-customer", installationId, 1, 0, 499);

        ExecutionUsageEvent usageEvent = gate.finalizeUsage(
                allowed.reservation(),
                new ExecutionUsageFinalization(ExecutionOutcome.SUCCEEDED, 1, 0, 0, null));

        assertEquals(allowed.reservation().reservationId(), usageEvent.reservation().reservationId());
        assertCredits(gateway, activeLicenseId, "contract-customer", installationId, 0, 1, 499);

        CreditDecision missingLicense = gate.reserve(ExecutionCreditRequest.billable(
                ExecutionType.REST_SERVICE_CALL,
                "contract.rest.missing_license",
                "missing-customer",
                UUID.randomUUID().toString(),
                "missing-installation"));

        assertEquals(CreditDenialReason.LICENSE_REQUIRED, missingLicense.denialReason());

        UUID exhaustedLicenseId = UUID.randomUUID();
        String exhaustedInstallation = "contract-installation-" + exhaustedLicenseId;
        gateway.importLicense(licenseImportParams(
                exhaustedLicenseId,
                "exhausted-customer",
                exhaustedInstallation,
                0));

        CreditDecision exhausted = gate.reserve(ExecutionCreditRequest.billable(
                ExecutionType.REST_SERVICE_CALL,
                "contract.rest.exhausted",
                "exhausted-customer",
                exhaustedLicenseId.toString(),
                exhaustedInstallation));

        assertEquals(CreditDenialReason.CREDITS_EXHAUSTED, exhausted.denialReason());
    }

    @Test
    void licenseReimportPreservesConsumedCredits() {
        InMemoryExecutionCreditLedger ledger = new InMemoryExecutionCreditLedger();
        ExecutionCreditDbGateway gateway = ledger.gateway();
        DbExecutionCreditGate gate = new DbExecutionCreditGate(new DefaultExecutionCreditPolicy(), gateway);
        UUID licenseId = UUID.randomUUID();
        String installationId = "contract-import-installation-" + licenseId;
        JsonObject importParams = licenseImportParams(
                licenseId,
                "contract-import-customer",
                installationId,
                500);

        JsonObject importResult = gateway.importLicense(importParams);
        assertTrue(importResult.get("imported").getAsBoolean());
        assertEquals(500, importResult.getAsJsonObject("credits").get("available").getAsLong());

        CreditDecision allowed = gate.reserve(ExecutionCreditRequest.billable(
                ExecutionType.REST_SERVICE_CALL,
                "contract.rest.imported_license",
                "contract-import-customer",
                licenseId.toString(),
                installationId));
        gate.finalizeUsage(
                allowed.reservation(),
                new ExecutionUsageFinalization(ExecutionOutcome.SUCCEEDED, 1, 0, 0, null));

        JsonObject reimportResult = gateway.importLicense(importParams);
        assertTrue(reimportResult.get("imported").getAsBoolean());
        assertEquals(1, reimportResult.getAsJsonObject("credits").get("consumed").getAsLong());
        assertEquals(499, reimportResult.getAsJsonObject("credits").get("available").getAsLong());
        assertCredits(gateway, licenseId, "contract-import-customer", installationId, 0, 1, 499);
    }

    private JsonObject licenseImportParams(UUID licenseId,
                                           String customerId,
                                           String installationId,
                                           long totalCredits) {
        JsonObject params = new JsonObject();
        params.addProperty("licenseId", licenseId.toString());
        params.addProperty("licenseType", "local_trial");
        params.addProperty("customerId", customerId);
        params.addProperty("installationId", installationId);
        params.addProperty("totalCredits", totalCredits);
        params.addProperty("validFrom", "2026-06-15T00:00:00Z");
        params.addProperty("issuedAt", "2026-06-15T00:00:00Z");
        params.addProperty("keyId", "contract-test");
        params.add("metadata", new JsonObject());
        return params;
    }

    private void assertCredits(ExecutionCreditDbGateway gateway,
                               UUID licenseId,
                               String customerId,
                               String installationId,
                               long expectedReserved,
                               long expectedConsumed,
                               long expectedAvailable) {
        JsonObject params = new JsonObject();
        params.addProperty("licenseId", licenseId.toString());
        params.addProperty("customerId", customerId);
        params.addProperty("installationId", installationId);
        JsonObject credits = gateway.status(params).getAsJsonObject("credits");
        assertEquals(expectedReserved, credits.get("reserved").getAsLong());
        assertEquals(expectedConsumed, credits.get("consumed").getAsLong());
        assertEquals(expectedAvailable, credits.get("available").getAsLong());
    }
}
