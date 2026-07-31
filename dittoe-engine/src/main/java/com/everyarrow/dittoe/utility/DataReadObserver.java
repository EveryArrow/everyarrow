package com.everyarrow.dittoe.utility;

import com.everyarrow.dittoe.pipelines.execution.PipelineExecutionStats;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

public class DataReadObserver {
    private boolean recordRead = false;
    private long rowsRead = 0;
    private long rowsErrored = 0;
    private PipelineExecutionStats stats;
    private Integer errorCountThreshold;

    public void recordRead() {
        this.recordRead = true;
        this.rowsRead++;
        if (stats != null) {
            stats.incrementRowsRead(1);
        }
    }

    public void recordRead(long count) {
        if (count > 0) {
            this.recordRead = true;
        }
        this.rowsRead += count;
        if (stats != null) {
            stats.incrementRowsRead(count);
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

    public boolean isRecordRead() {
        return recordRead;
    }

    public long getRowsRead() {
        return rowsRead;
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
                String.format("Pipeline failed: Error count threshold exceeded. Threshold: %d, Errors: %d",
                    this.errorCountThreshold, this.rowsErrored)
            );
        }
    }
}
