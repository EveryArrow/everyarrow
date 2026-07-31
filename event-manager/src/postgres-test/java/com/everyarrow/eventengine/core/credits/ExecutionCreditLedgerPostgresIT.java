package com.everyarrow.eventengine.core.credits;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Explicit PostgreSQL integration test for the operator-applied finance execution ledger.
 *
 * Required system properties:
 * - credit.ledger.jdbcUrl
 * - credit.ledger.dbUser
 * - credit.ledger.dbPassword
 *
 * The test inserts trial license rows inside a transaction and rolls back.
 */
class ExecutionCreditLedgerPostgresIT {

    @Test
    void reserveFinalizeMissingLicenseAndExhaustedCreditsWorkAgainstFinanceLedger() throws Exception {
        String jdbcUrl = requireProperty("credit.ledger.jdbcUrl");
        String dbUser = requireProperty("credit.ledger.dbUser");
        String dbPassword = requireProperty("credit.ledger.dbPassword");

        try (Connection connection = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword)) {
            connection.setAutoCommit(false);
            try {
                DbExecutionCreditGate gate = new DbExecutionCreditGate(
                        new DefaultExecutionCreditPolicy(),
                        new ExecutionCreditDbGateway((database, schema, function, jsonType, params) ->
                                executeFinanceFunction(connection, function, params)));

                UUID activeLicenseId = UUID.randomUUID();
                String installationId = "smoke-installation-" + activeLicenseId;
                insertTrialLicense(connection, activeLicenseId, "smoke-customer", installationId, 500);

                CreditDecision allowed = gate.reserve(ExecutionCreditRequest.billable(
                        ExecutionType.REST_SERVICE_CALL,
                        "smoke.rest.execute",
                        "smoke-customer",
                        activeLicenseId.toString(),
                        installationId));

                assertTrue(allowed.allowed());
                assertEquals(1, allowed.reservation().creditsReserved());
                assertLicenseCredits(connection, activeLicenseId, 1, 0);

                ExecutionUsageEvent usageEvent = gate.finalizeUsage(
                        allowed.reservation(),
                        new ExecutionUsageFinalization(ExecutionOutcome.SUCCEEDED, 1, 0, 0, null));

                assertEquals(allowed.reservation().reservationId(), usageEvent.reservation().reservationId());
                assertLicenseCredits(connection, activeLicenseId, 0, 1);

                CreditDecision missingLicense = gate.reserve(ExecutionCreditRequest.billable(
                        ExecutionType.REST_SERVICE_CALL,
                        "smoke.rest.missing_license",
                        "missing-customer",
                        UUID.randomUUID().toString(),
                        "missing-installation"));

                assertEquals(CreditDenialReason.LICENSE_REQUIRED, missingLicense.denialReason());

                UUID exhaustedLicenseId = UUID.randomUUID();
                insertTrialLicense(connection, exhaustedLicenseId, "exhausted-customer",
                        "smoke-installation-" + exhaustedLicenseId, 0);

                CreditDecision exhausted = gate.reserve(ExecutionCreditRequest.billable(
                        ExecutionType.REST_SERVICE_CALL,
                        "smoke.rest.exhausted",
                        "exhausted-customer",
                        exhaustedLicenseId.toString(),
                        "smoke-installation-" + exhaustedLicenseId));

                assertEquals(CreditDenialReason.CREDITS_EXHAUSTED, exhausted.denialReason());
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void licenseImportPreservesConsumedCreditsAgainstFinanceLedger() throws Exception {
        String jdbcUrl = requireProperty("credit.ledger.jdbcUrl");
        String dbUser = requireProperty("credit.ledger.dbUser");
        String dbPassword = requireProperty("credit.ledger.dbPassword");

        try (Connection connection = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword)) {
            connection.setAutoCommit(false);
            try {
                UUID licenseId = UUID.randomUUID();
                String installationId = "smoke-import-installation-" + licenseId;
                JsonObject importParams = licenseImportParams(
                        licenseId,
                        "smoke-import-customer",
                        installationId,
                        500);

                JsonObject importResult = executeFinanceFunction(connection, "execution_license_import", importParams);
                assertTrue(importResult.get("imported").getAsBoolean());
                assertEquals(500, importResult.getAsJsonObject("credits").get("available").getAsLong());

                DbExecutionCreditGate gate = new DbExecutionCreditGate(
                        new DefaultExecutionCreditPolicy(),
                        new ExecutionCreditDbGateway((database, schema, function, jsonType, params) ->
                                executeFinanceFunction(connection, function, params)));
                CreditDecision allowed = gate.reserve(ExecutionCreditRequest.billable(
                        ExecutionType.REST_SERVICE_CALL,
                        "smoke.rest.imported_license",
                        "smoke-import-customer",
                        licenseId.toString(),
                        installationId));
                gate.finalizeUsage(
                        allowed.reservation(),
                        new ExecutionUsageFinalization(ExecutionOutcome.SUCCEEDED, 1, 0, 0, null));
                assertLicenseCredits(connection, licenseId, 0, 1);

                JsonObject reimportResult = executeFinanceFunction(connection, "execution_license_import", importParams);
                assertTrue(reimportResult.get("imported").getAsBoolean());
                assertEquals(1, reimportResult.getAsJsonObject("credits").get("consumed").getAsLong());
                assertEquals(499, reimportResult.getAsJsonObject("credits").get("available").getAsLong());
                assertLicenseCredits(connection, licenseId, 0, 1);
            } finally {
                connection.rollback();
            }
        }
    }

    private String requireProperty(String key) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required system property: " + key);
        }
        return value;
    }

    private void insertTrialLicense(Connection connection,
                                    UUID licenseId,
                                    String customerId,
                                    String installationId,
                                    long totalCredits) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into finance.execution_license (
                    id,
                    license_type,
                    status,
                    customer_id,
                    installation_id,
                    total_credits,
                    metadata
                ) values (?::uuid, 'local_trial', 'active', ?, ?, ?, '{}'::jsonb)
                """)) {
            statement.setString(1, licenseId.toString());
            statement.setString(2, customerId);
            statement.setString(3, installationId);
            statement.setLong(4, totalCredits);
            statement.executeUpdate();
        }
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
        params.addProperty("keyId", "smoke-test");
        JsonObject metadata = new JsonObject();
        metadata.addProperty("seededBy", "ExecutionCreditLedgerPostgresIT");
        params.add("metadata", metadata);
        return params;
    }

    private JsonObject executeFinanceFunction(Connection connection, String function, JsonObject params) {
        String sql = "select * from finance." + function + "(?::jsonb)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, params == null ? "{}" : params.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return new JsonObject();
                }
                String json = jsonText(resultSet.getObject(1));
                JsonElement parsed = JsonParser.parseString(json);
                return parsed == null || !parsed.isJsonObject() ? new JsonObject() : parsed.getAsJsonObject();
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to execute finance." + function, ex);
        }
    }

    private void assertLicenseCredits(Connection connection,
                                      UUID licenseId,
                                      long expectedReserved,
                                      long expectedConsumed) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                select credits_reserved, credits_consumed
                from finance.execution_license
                where id = ?::uuid
                """)) {
            statement.setString(1, licenseId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next(), "Expected smoke license row to exist");
                assertEquals(expectedReserved, resultSet.getLong("credits_reserved"));
                assertEquals(expectedConsumed, resultSet.getLong("credits_consumed"));
            }
        }
    }

    private String jsonText(Object raw) throws Exception {
        if (raw == null) {
            return "{}";
        }
        if (raw.getClass().getName().equals("org.postgresql.util.PGobject")) {
            Object value = raw.getClass().getMethod("getValue").invoke(raw);
            return value == null ? "{}" : value.toString();
        }
        return raw.toString();
    }
}
