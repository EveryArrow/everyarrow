package com.everyarrow.eventengine.core.credits;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DbExecutionCreditGateTest {

    @Test
    void dbReserveAllowedMapsToAllowedDecision() {
        ExecutionCreditDbGateway gateway = new ExecutionCreditDbGateway((database, schema, function, jsonType, params) -> {
            JsonObject result = new JsonObject();
            result.addProperty("allowed", true);
            result.addProperty("reservationId", "00000000-0000-0000-0000-000000000101");
            result.addProperty("creditsRequired", params.get("creditsRequired").getAsLong());
            result.addProperty("creditsAvailable", 499);
            result.addProperty("creditsReserved", 1);
            return result;
        });
        DbExecutionCreditGate gate = new DbExecutionCreditGate(new DefaultExecutionCreditPolicy(), gateway);

        CreditDecision decision = gate.reserve(billableRequest());

        assertTrue(decision.allowed());
        assertEquals("00000000-0000-0000-0000-000000000101",
                decision.reservation().reservationId().toString());
        assertEquals(1, decision.reservation().creditsReserved());
    }

    @Test
    void dbReserveDeniedMapsToDeniedDecision() {
        ExecutionCreditDbGateway gateway = new ExecutionCreditDbGateway((database, schema, function, jsonType, params) -> {
            JsonObject result = new JsonObject();
            result.addProperty("allowed", false);
            result.addProperty("reason", CreditDenialReason.CREDITS_EXHAUSTED.name());
            result.addProperty("creditsRequired", 1);
            result.addProperty("creditsAvailable", 0);
            result.addProperty("message", "Upgrade required");
            return result;
        });
        DbExecutionCreditGate gate = new DbExecutionCreditGate(new DefaultExecutionCreditPolicy(), gateway);

        CreditDecision decision = gate.reserve(billableRequest());

        assertFalse(decision.allowed());
        assertEquals(CreditDenialReason.CREDITS_EXHAUSTED, decision.denialReason());
        assertEquals(1, decision.creditsRequired());
        assertEquals(0, decision.creditsAvailable());
        assertEquals("Upgrade required", decision.message());
    }

    @Test
    void dbExceptionDeniesWithLedgerUnavailable() {
        ExecutionCreditDbGateway gateway = new ExecutionCreditDbGateway((database, schema, function, jsonType, params) -> {
            throw new IllegalStateException("database down");
        });
        DbExecutionCreditGate gate = new DbExecutionCreditGate(new DefaultExecutionCreditPolicy(), gateway);

        CreditDecision decision = gate.reserve(billableRequest());

        assertFalse(decision.allowed());
        assertEquals(CreditDenialReason.LEDGER_UNAVAILABLE, decision.denialReason());
        assertEquals(1, decision.creditsRequired());
        assertEquals(0, decision.creditsAvailable());
    }

    @Test
    void nonBillableRequestsDoNotCallDbGateway() {
        AtomicInteger calls = new AtomicInteger();
        ExecutionCreditDbGateway gateway = new ExecutionCreditDbGateway((database, schema, function, jsonType, params) -> {
            calls.incrementAndGet();
            throw new AssertionError("non-billable requests should not call the ledger");
        });
        DbExecutionCreditGate gate = new DbExecutionCreditGate(new DefaultExecutionCreditPolicy(), gateway);

        CreditDecision decision = gate.reserve(new ExecutionCreditRequest(
                ExecutionType.REST_SERVICE_CALL,
                "platform.health",
                "customer-1",
                "license-1",
                "installation-1",
                java.util.Set.of(ExecutionCreditExclusionReason.HEALTH_CHECK)));

        assertTrue(decision.allowed());
        assertEquals(0, decision.reservation().creditsReserved());
        assertEquals(0, calls.get());
    }

    private ExecutionCreditRequest billableRequest() {
        return ExecutionCreditRequest.billable(
                ExecutionType.REST_SERVICE_CALL,
                "demo.execute",
                "customer-1",
                "00000000-0000-0000-0000-000000000001",
                "installation-1");
    }
}
