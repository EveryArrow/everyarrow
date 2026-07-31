package com.everyarrow.eventengine.core.credits;

/**
 * Local production gate backed by the finance execution-credit ledger.
 */
public final class DbExecutionCreditGate implements ExecutionCreditGate {
    private static final String LEDGER_UNAVAILABLE_MESSAGE = "Execution credit ledger unavailable";

    private final ExecutionCreditPolicy policy;
    private final ExecutionCreditDbGateway dbGateway;

    public DbExecutionCreditGate(ExecutionCreditPolicy policy,
                                 ExecutionCreditDbGateway dbGateway) {
        if (policy == null) {
            throw new IllegalArgumentException("policy is required");
        }
        if (dbGateway == null) {
            throw new IllegalArgumentException("dbGateway is required");
        }
        this.policy = policy;
        this.dbGateway = dbGateway;
    }

    @Override
    public CreditDecision reserve(ExecutionCreditRequest request) {
        long creditsRequired = policy.reservedCreditsFor(request);
        if (!request.billable()) {
            return CreditDecision.allowed(CreditReservation.create(request, creditsRequired));
        }

        try {
            return dbGateway.reserve(request, creditsRequired);
        } catch (RuntimeException ex) {
            return CreditDecision.denied(
                    CreditDenialReason.LEDGER_UNAVAILABLE,
                    creditsRequired,
                    0,
                    LEDGER_UNAVAILABLE_MESSAGE);
        }
    }

    @Override
    public ExecutionUsageEvent finalizeUsage(CreditReservation reservation,
                                             ExecutionUsageFinalization finalization) {
        if (reservation == null) {
            throw new IllegalArgumentException("reservation is required");
        }
        if (!reservation.request().billable()) {
            return ExecutionUsageEvent.create(reservation, finalization);
        }
        return dbGateway.finalizeUsage(reservation, finalization);
    }
}
