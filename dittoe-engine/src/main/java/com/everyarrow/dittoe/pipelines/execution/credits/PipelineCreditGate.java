package com.everyarrow.dittoe.pipelines.execution.credits;

import com.everyarrow.dittoe.pipelines.execution.PipelineExecutionStats;

/**
 * Boundary implemented later by local ledger and cloud entitlement gates.
 */
public interface PipelineCreditGate {
    PipelineCreditReservation reserve(PipelineCreditRequest request);

    PipelineUsageEvent finalizeUsage(PipelineCreditReservation reservation,
                                     PipelineExecutionStats stats,
                                     PipelineExecutionOutcome outcome);
}
