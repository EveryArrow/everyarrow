package com.everyarrow.dittoe.utility;

import com.everyarrow.dittoe.pipelines.execution.PipelineExecutionStats;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

public class DataInsertionObserver {
    private boolean recordInserted = false;
    private long rowsWritten = 0;
    private long rowsErrored = 0;
    private PipelineExecutionStats stats;
    private Integer errorCountThreshold;

    public void recordInserted() {
        this.recordInserted = true;
        this.rowsWritten++;
        if (stats != null) {
            stats.incrementRowsWritten(1);
        }
    }

    public void recordInserted(long count) {
        if (count > 0) {
            this.recordInserted = true;
        }
        this.rowsWritten += count;
        if (stats != null) {
            stats.incrementRowsWritten(count);
        }
    }

    public void recordErrored() throws ExecutionException {
        this.rowsErrored++;
        if (stats != null) {
            stats.incrementErrorRowCount(1);
        }
        checkErrorThreshold();
    }

    public void recordErrored(long count) throws ExecutionException {
        this.rowsErrored += count;
        if (stats != null) {
            stats.incrementErrorRowCount(count);
        }
        checkErrorThreshold();
    }

    public boolean isRecordInserted() {
        return recordInserted;
    }

    public long getRowsWritten() {
        return rowsWritten;
    }

    public long getRowsErrored() {
        return rowsErrored;
    }

    public void setStats(PipelineExecutionStats stats) {
        this.stats = stats;
    }

    public Integer getErrorCountThreshold() {
        return errorCountThreshold;
    }

    public void setErrorCountThreshold(Integer errorCountThreshold) {
        this.errorCountThreshold = errorCountThreshold;
    }

    private void checkErrorThreshold() throws ExecutionException {
        if (this.errorCountThreshold != null && this.rowsErrored > this.errorCountThreshold) {
            throw new ExecutionException(
                String.format("Pipeline failed: Error count threshold exceeded. Threshold: %d",
                    this.errorCountThreshold)
            );
        }
    }
}