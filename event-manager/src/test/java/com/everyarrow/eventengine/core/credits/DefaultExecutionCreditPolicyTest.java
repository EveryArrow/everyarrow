package com.everyarrow.eventengine.core.credits;

import com.everyarrow.eventengine.core.services.runtime.ServiceCallSource;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultExecutionCreditPolicyTest {
    private final DefaultExecutionCreditPolicy policy = new DefaultExecutionCreditPolicy();

    @Test
    void mapsServiceCallSourcesToExecutionTypes() {
        assertEquals(ExecutionType.REST_SERVICE_CALL, ExecutionType.fromServiceCallSource(ServiceCallSource.REST));
        assertEquals(ExecutionType.WEBHOOK_SERVICE_CALL, ExecutionType.fromServiceCallSource(ServiceCallSource.WEBHOOK));
        assertEquals(ExecutionType.QUEUE_MESSAGE, ExecutionType.fromServiceCallSource(ServiceCallSource.QUEUE));
        assertEquals(ExecutionType.SCHEDULED_JOB, ExecutionType.fromServiceCallSource(ServiceCallSource.SCHEDULE));
        assertEquals(ExecutionType.WEBSOCKET_COMMAND, ExecutionType.fromServiceCallSource(ServiceCallSource.WEBSOCKET));
        assertEquals(ExecutionType.INTERNAL_SERVICE_CALL, ExecutionType.fromServiceCallSource(ServiceCallSource.INTERNAL));
        assertEquals(ExecutionType.INTERNAL_SERVICE_CALL, ExecutionType.fromServiceCallSource(null));
    }

    @Test
    void pricesFixedCostExecutions() {
        assertEquals(1, reservedCreditsFor(ExecutionType.REST_SERVICE_CALL));
        assertEquals(1, reservedCreditsFor(ExecutionType.WEBHOOK_SERVICE_CALL));
        assertEquals(1, reservedCreditsFor(ExecutionType.INTERNAL_SERVICE_CALL));
        assertEquals(3, reservedCreditsFor(ExecutionType.QUEUE_MESSAGE));
        assertEquals(3, reservedCreditsFor(ExecutionType.SCHEDULED_JOB));
        assertEquals(10, reservedCreditsFor(ExecutionType.WEBSOCKET_COMMAND));
        assertEquals(1, reservedCreditsFor(ExecutionType.PIPELINE_RUN));
    }

    @Test
    void skipsNonBillableRequests() {
        ExecutionCreditRequest request = new ExecutionCreditRequest(
                ExecutionType.REST_SERVICE_CALL,
                "security.health",
                "customer-1",
                "license-1",
                "install-1",
                Set.of(ExecutionCreditExclusionReason.HEALTH_CHECK));

        assertFalse(request.billable());
        assertEquals(0, policy.reservedCreditsFor(request));
    }

    @Test
    void calculatesPipelineCreditsByThousandRows() {
        assertEquals(1, policy.pipelineCredits(0, 0));
        assertEquals(1, policy.pipelineCredits(1, 0));
        assertEquals(1, policy.pipelineCredits(999, 0));
        assertEquals(1, policy.pipelineCredits(1_000, 0));
        assertEquals(2, policy.pipelineCredits(1_001, 0));
        assertEquals(2, policy.pipelineCredits(5, 1_001));
    }

    @Test
    void keepsFuturePersistenceInFinanceSchema() {
        assertEquals("everyarrow_development", ExecutionCreditDataModel.DATABASE_NAME);
        assertEquals("finance", ExecutionCreditDataModel.SCHEMA_NAME);
    }

    @Test
    void rejectsNegativePipelineRows() {
        assertThrows(IllegalArgumentException.class, () -> policy.pipelineCredits(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> policy.pipelineCredits(0, -1));
    }

    @Test
    void createsAllowedAndDeniedDecisions() {
        ExecutionCreditRequest request = ExecutionCreditRequest.billable(
                ExecutionType.REST_SERVICE_CALL,
                "demo.call",
                "customer-1",
                "license-1",
                "install-1");
        CreditReservation reservation = CreditReservation.create(request, policy.reservedCreditsFor(request));
        CreditDecision allowed = CreditDecision.allowed(reservation);
        CreditDecision denied = CreditDecision.denied(CreditDenialReason.CREDITS_EXHAUSTED, 1, 0, "Upgrade required");

        assertTrue(allowed.allowed());
        assertEquals(reservation, allowed.reservation());
        assertFalse(denied.allowed());
        assertEquals(CreditDenialReason.CREDITS_EXHAUSTED, denied.denialReason());
    }

    private long reservedCreditsFor(ExecutionType executionType) {
        return policy.reservedCreditsFor(ExecutionCreditRequest.billable(
                executionType,
                "demo.call",
                "customer-1",
                "license-1",
                "install-1"));
    }
}
