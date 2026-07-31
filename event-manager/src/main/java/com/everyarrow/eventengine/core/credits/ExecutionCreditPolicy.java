package com.everyarrow.eventengine.core.credits;

/**
 * Computes credit costs without owning license or ledger state.
 */
public interface ExecutionCreditPolicy {
    long reservedCreditsFor(ExecutionCreditRequest request);

    long pipelineCredits(long rowsRead, long rowsWritten);
}
