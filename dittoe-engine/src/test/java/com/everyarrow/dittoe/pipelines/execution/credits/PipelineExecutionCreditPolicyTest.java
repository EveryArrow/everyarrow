package com.everyarrow.dittoe.pipelines.execution.credits;

import com.everyarrow.dittoe.pipelines.execution.PipelineExecutionStats;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PipelineExecutionCreditPolicyTest {
    private final PipelineExecutionCreditPolicy policy = new PipelineExecutionCreditPolicy();

    @Test
    void reservesMinimumCreditBeforeRun() {
        PipelineCreditRequest request = new PipelineCreditRequest(
                "daily-sync",
                "customer-1",
                "license-1",
                "install-1");

        assertEquals(1, policy.reservedCreditsFor(request));
    }

    @Test
    void calculatesCreditsFromRowsReadOrWritten() {
        assertEquals(1, policy.creditsForRows(0, 0));
        assertEquals(1, policy.creditsForRows(1, 0));
        assertEquals(1, policy.creditsForRows(999, 0));
        assertEquals(1, policy.creditsForRows(1_000, 0));
        assertEquals(2, policy.creditsForRows(1_001, 0));
        assertEquals(2, policy.creditsForRows(5, 1_001));
    }

    @Test
    void calculatesCreditsFromPipelineStats() {
        PipelineExecutionStats stats = new PipelineExecutionStats();
        stats.setRowsRead(500);
        stats.setRowsWritten(2_100);

        assertEquals(3, policy.creditsFor(stats));
    }

    @Test
    void recordsFuturePersistenceTarget() {
        assertEquals("everyarrow_development", PipelineExecutionCreditPolicy.DATABASE_NAME);
        assertEquals("finance", PipelineExecutionCreditPolicy.SCHEMA_NAME);
    }

    @Test
    void rejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> policy.reservedCreditsFor(null));
        assertThrows(IllegalArgumentException.class, () -> policy.creditsFor(null));
        assertThrows(IllegalArgumentException.class, () -> policy.creditsForRows(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> policy.creditsForRows(0, -1));
        assertThrows(IllegalArgumentException.class, () -> new PipelineCreditRequest(" ", null, null, null));
    }
}
