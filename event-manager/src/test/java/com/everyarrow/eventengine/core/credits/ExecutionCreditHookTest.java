package com.everyarrow.eventengine.core.credits;

import com.everyarrow.eventengine.core.hooks.SecurityHook;
import com.everyarrow.eventengine.core.services.runtime.InternalCallMetadata;
import com.everyarrow.eventengine.core.services.runtime.ServiceCall;
import com.everyarrow.eventengine.core.services.runtime.ServiceCallSource;
import com.everyarrow.utilities.environment.Environment;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionCreditHookTest {

    @AfterEach
    void resetEnvironment() {
        Environment.reset();
    }

    @Test
    void reservesFixedCreditsByServiceCallSource() {
        assertReservedCredits(ServiceCallSource.REST, 1);
        assertReservedCredits(ServiceCallSource.QUEUE, 3);
        assertReservedCredits(ServiceCallSource.SCHEDULE, 3);
        assertReservedCredits(ServiceCallSource.WEBSOCKET, 10);
    }

    @Test
    void skipsSecurityInternalCalls() {
        CapturingGate gate = new CapturingGate();
        ExecutionCreditHook hook = new ExecutionCreditHook(gate);
        ServiceCall call = serviceCall(ServiceCallSource.INTERNAL);
        call.putMetadata(SecurityHook.INTERNAL_SECURITY_CALL_METADATA, true);

        hook.before(call);

        CreditReservation reservation = reservation(call);
        assertEquals(0, reservation.creditsReserved());
        assertFalse(reservation.request().billable());
        assertTrue(reservation.request().exclusionReasons()
                .contains(ExecutionCreditExclusionReason.INTERNAL_SECURITY_CALL));
    }

    @Test
    void skipsPerchInternalCalls() {
        CapturingGate gate = new CapturingGate();
        ExecutionCreditHook hook = new ExecutionCreditHook(gate);
        ServiceCall call = serviceCall(ServiceCallSource.INTERNAL);
        call.putMetadata(InternalCallMetadata.PERCH_LIFECYCLE_CALL, "true");

        hook.before(call);

        CreditReservation reservation = reservation(call);
        assertEquals(0, reservation.creditsReserved());
        assertFalse(reservation.request().billable());
        assertTrue(reservation.request().exclusionReasons()
                .contains(ExecutionCreditExclusionReason.INTERNAL_PERCH_CALL));
    }

    @Test
    void skipsPlatformLicenseCreditUsageHealthAndAttestationCalls() {
        assertSkippedByServiceType("platform.health", ExecutionCreditExclusionReason.HEALTH_CHECK);
        assertSkippedByServiceType("platform.readiness", ExecutionCreditExclusionReason.READINESS_CHECK);
        assertSkippedByServiceType("license.check", ExecutionCreditExclusionReason.LICENSE_CHECK);
        assertSkippedByServiceType("finance.credit.balance", ExecutionCreditExclusionReason.CREDIT_OR_USAGE_CALL);
        assertSkippedByServiceType("finance.usage.summary", ExecutionCreditExclusionReason.CREDIT_OR_USAGE_CALL);
        assertSkippedByServiceType(ExecutionCreditServiceContract.STATUS,
                ExecutionCreditExclusionReason.CREDIT_OR_USAGE_CALL);
        assertSkippedByServiceType(ExecutionCreditServiceContract.USAGE_SUMMARY,
                ExecutionCreditExclusionReason.CREDIT_OR_USAGE_CALL);
        assertSkippedByServiceType(ExecutionCreditServiceContract.LICENSE_IMPORT,
                ExecutionCreditExclusionReason.LICENSE_CHECK);
        assertSkippedByServiceType("runtime.attestation.verify", ExecutionCreditExclusionReason.ATTESTATION_CALL);
    }

    @Test
    void successFinalizesSucceeded() {
        CapturingGate gate = new CapturingGate();
        ExecutionCreditHook hook = new ExecutionCreditHook(gate);
        ServiceCall call = serviceCall(ServiceCallSource.REST);

        hook.before(call);
        hook.after(call, new JsonObject());

        assertEquals(1, gate.finalizations.size());
        assertEquals(ExecutionOutcome.SUCCEEDED, gate.finalizations.get(0).outcome());
        assertEquals(1, gate.finalizations.get(0).creditsCharged());
        assertEquals(ExecutionOutcome.SUCCEEDED.name(), call.getMetadata(ExecutionCreditHook.METADATA_OUTCOME));
    }

    @Test
    void handlerFailureFinalizesFailed() {
        CapturingGate gate = new CapturingGate();
        ExecutionCreditHook hook = new ExecutionCreditHook(gate);
        ServiceCall call = serviceCall(ServiceCallSource.REST);

        hook.before(call);
        hook.onException(call, new IllegalStateException("handler failed"));

        assertEquals(1, gate.finalizations.size());
        ExecutionUsageFinalization finalization = gate.finalizations.get(0);
        assertEquals(ExecutionOutcome.FAILED, finalization.outcome());
        assertEquals("handler failed", finalization.message());
        assertEquals(ExecutionOutcome.FAILED.name(), call.getMetadata(ExecutionCreditHook.METADATA_OUTCOME));
    }

    @Test
    void requestContextFallsBackToExecutionCreditEnvironment() {
        Environment.put(ExecutionCreditHook.CUSTOMER_ID_CONFIG, "customer-env");
        Environment.put(ExecutionCreditHook.LICENSE_ID_CONFIG, "00000000-0000-0000-0000-000000000501");
        Environment.put(ExecutionCreditHook.INSTALLATION_ID_CONFIG, "installation-env");
        CapturingGate gate = new CapturingGate();
        ExecutionCreditHook hook = new ExecutionCreditHook(gate, Environment.getInstance());

        hook.before(serviceCall(ServiceCallSource.REST));

        ExecutionCreditRequest request = gate.requests.get(0);
        assertEquals("customer-env", request.customerId());
        assertEquals("00000000-0000-0000-0000-000000000501", request.licenseId());
        assertEquals("installation-env", request.installationId());
    }

    @Test
    void requestParamsOverrideExecutionCreditEnvironment() {
        Environment.put(ExecutionCreditHook.CUSTOMER_ID_CONFIG, "customer-env");
        Environment.put(ExecutionCreditHook.LICENSE_ID_CONFIG, "00000000-0000-0000-0000-000000000501");
        Environment.put(ExecutionCreditHook.INSTALLATION_ID_CONFIG, "installation-env");
        CapturingGate gate = new CapturingGate();
        ExecutionCreditHook hook = new ExecutionCreditHook(gate, Environment.getInstance());
        ServiceCall call = serviceCall(ServiceCallSource.REST);
        call.getParams().addProperty("customer_id", "customer-param");
        call.getParams().addProperty("license_id", "00000000-0000-0000-0000-000000000502");
        call.getParams().addProperty("installation_id", "installation-param");

        hook.before(call);

        ExecutionCreditRequest request = gate.requests.get(0);
        assertEquals("customer-param", request.customerId());
        assertEquals("00000000-0000-0000-0000-000000000502", request.licenseId());
        assertEquals("installation-param", request.installationId());
    }

    private void assertReservedCredits(ServiceCallSource source, long expectedCredits) {
        CapturingGate gate = new CapturingGate();
        ExecutionCreditHook hook = new ExecutionCreditHook(gate);
        ServiceCall call = serviceCall(source);

        hook.before(call);

        CreditReservation reservation = reservation(call);
        assertEquals(expectedCredits, reservation.creditsReserved());
        assertEquals(expectedCredits, call.getMetadata(ExecutionCreditHook.METADATA_CREDITS_RESERVED));
        assertEquals(ExecutionType.fromServiceCallSource(source), reservation.request().executionType());
    }

    private void assertSkippedByServiceType(String serviceType, ExecutionCreditExclusionReason expectedReason) {
        CapturingGate gate = new CapturingGate();
        ExecutionCreditHook hook = new ExecutionCreditHook(gate);
        ServiceCall call = serviceCall(ServiceCallSource.REST);
        call.setType(serviceType);

        hook.before(call);

        CreditReservation reservation = reservation(call);
        assertEquals(0, reservation.creditsReserved());
        assertFalse(reservation.request().billable());
        assertTrue(reservation.request().exclusionReasons().contains(expectedReason));
    }

    private ServiceCall serviceCall(ServiceCallSource source) {
        ServiceCall call = new ServiceCall();
        call.setType("demo.execute");
        call.setSource(source);
        call.setParams(new JsonObject());
        return call;
    }

    private CreditReservation reservation(ServiceCall call) {
        return (CreditReservation) call.getMetadata(ExecutionCreditHook.METADATA_RESERVATION);
    }

    private static final class CapturingGate implements ExecutionCreditGate {
        private final DefaultExecutionCreditPolicy policy = new DefaultExecutionCreditPolicy();
        private final List<ExecutionCreditRequest> requests = new ArrayList<>();
        private final List<ExecutionUsageFinalization> finalizations = new ArrayList<>();

        @Override
        public CreditDecision reserve(ExecutionCreditRequest request) {
            requests.add(request);
            return CreditDecision.allowed(CreditReservation.create(request, policy.reservedCreditsFor(request)));
        }

        @Override
        public ExecutionUsageEvent finalizeUsage(CreditReservation reservation,
                                                 ExecutionUsageFinalization finalization) {
            finalizations.add(finalization);
            return ExecutionUsageEvent.create(reservation, finalization);
        }
    }
}
