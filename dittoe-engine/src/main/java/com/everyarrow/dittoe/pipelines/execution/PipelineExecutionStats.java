package com.everyarrow.dittoe.pipelines.execution;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Tracks statistics for a pipeline execution.
 */
public class PipelineExecutionStats {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");
    
    private long rowsRead = 0;
    private long rowsWritten = 0;
    private long errorRowCount = 0;
    private boolean isJobBatched = false;
    private String dateStarted;
    private String dateFinished;
    private int retriesAttempted = 0;
    private List<StageStats> stages = new ArrayList<>();
    
    /**
     * Represents statistics for a single stage in the pipeline.
     */
    public static class StageStats {
        private String stageName;
        private String startTime;
        private String stopTime;
        private long rowsProcessed = 0;
        private long rowsErrored = 0;
        
        public StageStats(String stageName) {
            this.stageName = stageName;
        }
        
        public void start() {
            this.startTime = LocalDateTime.now().format(DATE_TIME_FORMATTER);
        }
        
        public void stop() {
            this.stopTime = LocalDateTime.now().format(DATE_TIME_FORMATTER);
        }
        
        public String getStageName() {
            return stageName;
        }
        
        public void setStageName(String stageName) {
            this.stageName = stageName;
        }
        
        public String getStartTime() {
            return startTime;
        }
        
        public void setStartTime(String startTime) {
            this.startTime = startTime;
        }
        
        public String getStopTime() {
            return stopTime;
        }
        
        public void setStopTime(String stopTime) {
            this.stopTime = stopTime;
        }
        
        public long getRowsProcessed() {
            return rowsProcessed;
        }
        
        public void setRowsProcessed(long rowsProcessed) {
            this.rowsProcessed = rowsProcessed;
        }
        
        public void incrementRowsProcessed(long count) {
            this.rowsProcessed += count;
        }
        
        public long getRowsErrored() {
            return rowsErrored;
        }
        
        public void setRowsErrored(long rowsErrored) {
            this.rowsErrored = rowsErrored;
        }
        
        public void incrementRowsErrored(long count) {
            this.rowsErrored += count;
        }
    }
    
    public void start() {
        this.dateStarted = LocalDateTime.now().format(DATE_TIME_FORMATTER);
    }
    
    public void finish() {
        this.dateFinished = LocalDateTime.now().format(DATE_TIME_FORMATTER);
    }
    
    public StageStats startStage(String stageName) {
        StageStats stageStats = new StageStats(stageName);
        stageStats.start();
        stages.add(stageStats);
        return stageStats;
    }
    
    public String toJson() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }
    
    // Getters and Setters
    
    public long getRowsRead() {
        return rowsRead;
    }
    
    public void setRowsRead(long rowsRead) {
        this.rowsRead = rowsRead;
    }
    
    public void incrementRowsRead(long count) {
        this.rowsRead += count;
    }
    
    public long getRowsWritten() {
        return rowsWritten;
    }
    
    public void setRowsWritten(long rowsWritten) {
        this.rowsWritten = rowsWritten;
    }
    
    public void incrementRowsWritten(long count) {
        this.rowsWritten += count;
    }
    
    public long getErrorRowCount() {
        return errorRowCount;
    }
    
    public void setErrorRowCount(long errorRowCount) {
        this.errorRowCount = errorRowCount;
    }
    
    public void incrementErrorRowCount(long count) {
        this.errorRowCount += count;
    }
    
    public boolean isJobBatched() {
        return isJobBatched;
    }
    
    public void setJobBatched(boolean jobBatched) {
        isJobBatched = jobBatched;
    }
    
    public String getDateStarted() {
        return dateStarted;
    }
    
    public void setDateStarted(String dateStarted) {
        this.dateStarted = dateStarted;
    }
    
    public String getDateFinished() {
        return dateFinished;
    }
    
    public void setDateFinished(String dateFinished) {
        this.dateFinished = dateFinished;
    }
    
    public int getRetriesAttempted() {
        return retriesAttempted;
    }
    
    public void setRetriesAttempted(int retriesAttempted) {
        this.retriesAttempted = retriesAttempted;
    }
    
    public void incrementRetriesAttempted() {
        this.retriesAttempted++;
    }
    
    public List<StageStats> getStages() {
        return stages;
    }
    
    public void setStages(List<StageStats> stages) {
        this.stages = stages;
    }
}