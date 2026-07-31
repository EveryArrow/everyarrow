package com.everyarrow.eventengine.core.credits;

import com.everyarrow.eventengine.core.services.runtime.ServiceCall;
import com.everyarrow.utilities.environment.Environment;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionCreditStatusServiceTest {

    @AfterEach
    void resetEnvironment() {
        Environment.reset();
    }

    @Test
    void statusInAllowingModeSkipsDbAndReportsInactiveEnforcement() {
        AtomicInteger calls = new AtomicInteger();
        ExecutionCreditStatusService service = new ExecutionCreditStatusService(
                Environment.getInstance(),
                failingGateway(calls),
                new ExecutionCreditContextResolver(Environment.getInstance()));

        JsonObject status = service.status(serviceCall());

        assertEquals(0, calls.get());
        assertEquals(ExecutionCreditGateFactory.MODE_ALLOWING, status.get("mode").getAsString());
        assertTrue(status.get("ledgerAvailable").getAsBoolean());
        assertFalse(status.get("enforcementActive").getAsBoolean());
        assertEquals(0, status.getAsJsonObject("credits").get("available").getAsLong());
        assertFalse(status.getAsJsonObject("upgrade").get("required").getAsBoolean());
    }

    @Test
    void localStatusMapsLicenseAndCreditFieldsFromLedger() {
        Environment.put(ExecutionCreditGateFactory.MODE_CONFIG, ExecutionCreditGateFactory.MODE_LOCAL);
        ExecutionCreditStatusService service = new ExecutionCreditStatusService(
                Environment.getInstance(),
                new ExecutionCreditDbGateway((database, schema, function, jsonType, params) -> {
                    JsonObject status = new JsonObject();
                    status.add("license", license());
                    JsonObject credits = new JsonObject();
                    credits.addProperty("total", 500);
                    credits.addProperty("reserved", 3);
                    credits.addProperty("consumed", 10);
                    credits.addProperty("available", 487);
                    status.add("credits", credits);
                    JsonObject upgrade = new JsonObject();
                    upgrade.addProperty("required", false);
                    status.add("upgrade", upgrade);
                    return status;
                }),
                new ExecutionCreditContextResolver(Environment.getInstance()));

        JsonObject status = service.status(serviceCall());

        assertEquals(ExecutionCreditGateFactory.MODE_LOCAL, status.get("mode").getAsString());
        assertTrue(status.get("ledgerAvailable").getAsBoolean());
        assertTrue(status.get("enforcementActive").getAsBoolean());
        assertEquals("local_trial", status.getAsJsonObject("license").get("licenseType").getAsString());
        assertEquals(487, status.getAsJsonObject("credits").get("available").getAsLong());
    }

    @Test
    void localStatusDbFailureReturnsLedgerUnavailablePayload() {
        Environment.put(ExecutionCreditGateFactory.MODE_CONFIG, ExecutionCreditGateFactory.MODE_LOCAL);
        ExecutionCreditStatusService service = new ExecutionCreditStatusService(
                Environment.getInstance(),
                new ExecutionCreditDbGateway((database, schema, function, jsonType, params) -> {
                    throw new IllegalStateException("database down");
                }),
                new ExecutionCreditContextResolver(Environment.getInstance()));

        JsonObject status = service.status(serviceCall());

        assertFalse(status.get("ledgerAvailable").getAsBoolean());
        assertTrue(status.get("enforcementActive").getAsBoolean());
        assertEquals(CreditDenialReason.LEDGER_UNAVAILABLE.name(), status.get("reason").getAsString());
        assertEquals(0, status.getAsJsonObject("credits").get("available").getAsLong());
    }

    @Test
    void usageSummaryMapsTotalsGroupedUsageAndContextParams() {
        Environment.put(ExecutionCreditGateFactory.MODE_CONFIG, ExecutionCreditGateFactory.MODE_LOCAL);
        AtomicReference<JsonObject> capturedParams = new AtomicReference<>();
        ExecutionCreditStatusService service = new ExecutionCreditStatusService(
                Environment.getInstance(),
                new ExecutionCreditDbGateway((database, schema, function, jsonType, params) -> {
                    capturedParams.set(params);
                    JsonObject usage = new JsonObject();
                    usage.addProperty("licenseId", "00000000-0000-0000-0000-000000000601");
                    usage.addProperty("from", params.get("from").getAsString());
                    usage.addProperty("to", params.get("to").getAsString());
                    usage.addProperty("totalEvents", 2);
                    usage.addProperty("creditsCharged", 11);
                    usage.addProperty("rowsRead", 1000);
                    usage.addProperty("rowsWritten", 900);
                    usage.addProperty("succeeded", 1);
                    usage.addProperty("failed", 1);
                    usage.add("byExecutionType", group("REST_SERVICE_CALL", null));
                    usage.add("byServiceType", group(null, "demo.execute"));
                    return usage;
                }),
                new ExecutionCreditContextResolver(Environment.getInstance()));
        ServiceCall call = serviceCall();
        call.getParams().addProperty("from", "2026-06-01T00:00:00Z");
        call.getParams().addProperty("to", "2026-06-15T00:00:00Z");
        call.getParams().addProperty("license_id", "00000000-0000-0000-0000-000000000601");

        JsonObject usage = service.usageSummary(call);

        assertEquals("00000000-0000-0000-0000-000000000601",
                capturedParams.get().get("licenseId").getAsString());
        assertEquals(2, usage.get("totalEvents").getAsLong());
        assertEquals(11, usage.get("creditsCharged").getAsLong());
        assertEquals("REST_SERVICE_CALL",
                usage.getAsJsonArray("byExecutionType").get(0).getAsJsonObject().get("executionType").getAsString());
        assertEquals("demo.execute",
                usage.getAsJsonArray("byServiceType").get(0).getAsJsonObject().get("serviceType").getAsString());
    }

    @Test
    void localUsageDbFailureReturnsZeroSummaryWithLedgerUnavailableReason() {
        Environment.put(ExecutionCreditGateFactory.MODE_CONFIG, ExecutionCreditGateFactory.MODE_LOCAL);
        ExecutionCreditStatusService service = new ExecutionCreditStatusService(
                Environment.getInstance(),
                new ExecutionCreditDbGateway((database, schema, function, jsonType, params) -> {
                    throw new IllegalStateException("database down");
                }),
                new ExecutionCreditContextResolver(Environment.getInstance()));

        JsonObject usage = service.usageSummary(serviceCall());

        assertFalse(usage.get("ledgerAvailable").getAsBoolean());
        assertEquals(CreditDenialReason.LEDGER_UNAVAILABLE.name(), usage.get("reason").getAsString());
        assertEquals(0, usage.get("totalEvents").getAsLong());
        assertEquals(0, usage.getAsJsonArray("byExecutionType").size());
    }

    private ExecutionCreditDbGateway failingGateway(AtomicInteger calls) {
        return new ExecutionCreditDbGateway((database, schema, function, jsonType, params) -> {
            calls.incrementAndGet();
            throw new AssertionError("allowing mode should not call the ledger");
        });
    }

    private ServiceCall serviceCall() {
        ServiceCall call = new ServiceCall();
        call.setType(ExecutionCreditServiceContract.STATUS);
        call.setParams(new JsonObject());
        return call;
    }

    private JsonObject license() {
        JsonObject license = new JsonObject();
        license.addProperty("id", "00000000-0000-0000-0000-000000000601");
        license.addProperty("licenseType", "local_trial");
        license.addProperty("status", "active");
        license.addProperty("customerId", "customer-1");
        license.addProperty("installationId", "installation-1");
        return license;
    }

    private JsonArray group(String executionType, String serviceType) {
        JsonArray groups = new JsonArray();
        JsonObject group = new JsonObject();
        if (executionType != null) {
            group.addProperty("executionType", executionType);
            group.addProperty("key", executionType);
        }
        if (serviceType != null) {
            group.addProperty("serviceType", serviceType);
            group.addProperty("key", serviceType);
        }
        group.addProperty("eventCount", 1);
        group.addProperty("creditsCharged", 1);
        group.addProperty("rowsRead", 0);
        group.addProperty("rowsWritten", 0);
        groups.add(group);
        return groups;
    }
}
