package com.everyarrow.eventengine.core.credits;

import com.everyarrow.eventengine.core.hooks.ServiceHook;
import com.everyarrow.eventengine.core.services.runtime.ServiceCall;
import com.everyarrow.utilities.environment.Environment;

/**
 * Reserves execution credits after security has resolved identity context and
 * finalizes the attempted usage before Perch receives completion metadata.
 */
public final class ExecutionCreditHook implements ServiceHook {
    public static final String CUSTOMER_ID_CONFIG = ExecutionCreditContextResolver.CUSTOMER_ID_CONFIG;
    public static final String LICENSE_ID_CONFIG = ExecutionCreditContextResolver.LICENSE_ID_CONFIG;
    public static final String INSTALLATION_ID_CONFIG = ExecutionCreditContextResolver.INSTALLATION_ID_CONFIG;
    public static final String METADATA_RESERVATION = "credit.reservation";
    public static final String METADATA_RESERVATION_ID = "credit.reservationId";
    public static final String METADATA_EXECUTION_TYPE = "credit.executionType";
    public static final String METADATA_CREDITS_RESERVED = "credit.creditsReserved";
    public static final String METADATA_USAGE_EVENT = "credit.usageEvent";
    public static final String METADATA_USAGE_EVENT_ID = "credit.usageEventId";
    public static final String METADATA_CREDITS_CHARGED = "credit.creditsCharged";
    public static final String METADATA_OUTCOME = "credit.outcome";

    private final ExecutionCreditGate creditGate;
    private final ExecutionCreditContextResolver contextResolver;

    public ExecutionCreditHook() {
        this(new AllowingExecutionCreditGate(new DefaultExecutionCreditPolicy()));
    }

    public ExecutionCreditHook(ExecutionCreditGate creditGate) {
        this(creditGate, Environment.getInstance());
    }

    ExecutionCreditHook(ExecutionCreditGate creditGate, Environment environment) {
        if (creditGate == null) {
            throw new IllegalArgumentException("creditGate is required");
        }
        this.creditGate = creditGate;
        this.contextResolver = new ExecutionCreditContextResolver(environment);
    }

    @Override
    public void before(ServiceCall call) {
        if (call == null) {
            return;
        }

        ExecutionCreditRequest request = requestFor(call);
        CreditDecision decision = creditGate.reserve(request);
        if (decision == null) {
            throw new IllegalStateException("Credit gate returned no decision");
        }
        if (!decision.allowed()) {
            throw new ExecutionCreditDeniedException(
                    decision.denialReason(),
                    decision.creditsRequired(),
                    decision.creditsAvailable(),
                    decision.message());
        }

        storeReservation(call, decision.reservation());
    }

    @Override
    public void after(ServiceCall call, Object result) {
        finalizeUsage(call, ExecutionOutcome.SUCCEEDED, null);
    }

    @Override
    public void onException(ServiceCall call, Exception ex) {
        finalizeUsage(call, ExecutionOutcome.FAILED, ex == null ? null : ex.getMessage());
    }

    private ExecutionCreditRequest requestFor(ServiceCall call) {
        return contextResolver.requestFor(call);
    }

    private void storeReservation(ServiceCall call, CreditReservation reservation) {
        call.putMetadata(METADATA_RESERVATION, reservation);
        call.putMetadata(METADATA_RESERVATION_ID, reservation.reservationId().toString());
        call.putMetadata(METADATA_EXECUTION_TYPE, reservation.request().executionType().name());
        call.putMetadata(METADATA_CREDITS_RESERVED, reservation.creditsReserved());
    }

    private void finalizeUsage(ServiceCall call, ExecutionOutcome outcome, String message) {
        CreditReservation reservation = reservationFrom(call);
        if (reservation == null) {
            return;
        }

        ExecutionUsageFinalization finalization = new ExecutionUsageFinalization(
                outcome,
                reservation.creditsReserved(),
                0,
                0,
                message);
        ExecutionUsageEvent usageEvent = creditGate.finalizeUsage(reservation, finalization);
        if (usageEvent == null) {
            return;
        }

        call.putMetadata(METADATA_USAGE_EVENT, usageEvent);
        call.putMetadata(METADATA_USAGE_EVENT_ID, usageEvent.usageEventId().toString());
        call.putMetadata(METADATA_CREDITS_CHARGED, finalization.creditsCharged());
        call.putMetadata(METADATA_OUTCOME, finalization.outcome().name());
    }

    private CreditReservation reservationFrom(ServiceCall call) {
        if (call == null) {
            return null;
        }
        Object value = call.getMetadata(METADATA_RESERVATION);
        return value instanceof CreditReservation reservation ? reservation : null;
    }

}
