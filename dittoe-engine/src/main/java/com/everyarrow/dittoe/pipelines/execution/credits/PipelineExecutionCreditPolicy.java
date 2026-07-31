package com.everyarrow.dittoe.pipelines.execution.credits;

import com.everyarrow.dittoe.pipelines.execution.PipelineExecutionStats;

/**
 * V1 row-based pipeline pricing.
 */
public final class PipelineExecutionCreditPolicy {
    public static final long MINIMUM_PIPELINE_CREDITS = 1;
    public static final long PIPELINE_ROWS_PER_CREDIT = 1_000;
    public static final String DATABASE_NAME = "everyarrow_development";
    public static final String SCHEMA_NAME = "finance";

    public long reservedCreditsFor(PipelineCreditRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        return MINIMUM_PIPELINE_CREDITS;
    }

    public long creditsFor(PipelineExecutionStats stats) {
        if (stats == null) {
            throw new IllegalArgumentException("stats is required");
        }
        return creditsForRows(stats.getRowsRead(), stats.getRowsWritten());
    }

    public long creditsForRows(long rowsRead, long rowsWritten) {
        if (rowsRead < 0 || rowsWritten < 0) {
            throw new IllegalArgumentException("row counts must be non-negative");
        }
        long billableRows = Math.max(rowsRead, rowsWritten);
        long roundedCredits = (billableRows + PIPELINE_ROWS_PER_CREDIT - 1) / PIPELINE_ROWS_PER_CREDIT;
        return Math.max(MINIMUM_PIPELINE_CREDITS, roundedCredits);
    }
}
