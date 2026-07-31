package com.everyarrow.eventengine.core.credits;

/**
 * Boundary implemented later by local ledger and cloud entitlement gates.
 */
public interface ExecutionCreditGate {
    CreditDecision reserve(ExecutionCreditRequest request);

    ExecutionUsageEvent finalizeUsage(CreditReservation reservation,
                                      ExecutionUsageFinalization finalization);
}
