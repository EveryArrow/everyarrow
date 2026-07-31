package com.everyarrow.eventengine.core.credits;

/**
 * Development/default gate that exercises reservation and finalization without
 * denying execution. License-backed and ledger-backed gates can replace this
 * boundary later without changing the hook contract.
 */
public final class AllowingExecutionCreditGate implements ExecutionCreditGate {
    private final ExecutionCreditPolicy policy;

    public AllowingExecutionCreditGate(ExecutionCreditPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("policy is required");
        }
        this.policy = policy;
    }

    @Override
    public CreditDecision reserve(ExecutionCreditRequest request) {
        long creditsReserved = policy.reservedCreditsFor(request);
        return CreditDecision.allowed(CreditReservation.create(request, creditsReserved));
    }

    @Override
    public ExecutionUsageEvent finalizeUsage(CreditReservation reservation,
                                             ExecutionUsageFinalization finalization) {
        return ExecutionUsageEvent.create(reservation, finalization);
    }
}
