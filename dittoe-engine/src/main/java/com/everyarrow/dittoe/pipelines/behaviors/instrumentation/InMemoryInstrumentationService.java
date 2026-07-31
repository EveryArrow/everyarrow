package com.everyarrow.dittoe.pipelines.behaviors.instrumentation;

import com.everyarrow.dittoe.pipelines.execution.abstraction.BehaviorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of InstrumentationService.
 * Stores human-readable instrumentation logs in memory and outputs them at the end.
 */
public class InMemoryInstrumentationService implements InstrumentationService {
    
    private static final Logger logger = LoggerFactory.getLogger(InMemoryInstrumentationService.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    private final List<String> logEntries;
    private final Map<InstrumentationTask, TaskExecutionHandle> runningTasks;
    private final Map<String, InstrumentationTask> tasksByName;
    private final Map<String, Long> taskStartTimes;
    private String jobName;
    private String jobExecutionId;
    
    public InMemoryInstrumentationService() {
        this.logEntries = new ArrayList<>();
        this.runningTasks = new ConcurrentHashMap<>();
        this.tasksByName = new ConcurrentHashMap<>();
        this.taskStartTimes = new ConcurrentHashMap<>();
    }
    
    @Override
    public void initialize(Map<String, String> properties) throws BehaviorException {
        this.jobName = properties.getOrDefault("jobName", "pipeline");
        this.jobExecutionId = properties.getOrDefault("jobExecutionId", UUID.randomUUID().toString());

        // Write header
        addLog("=".repeat(80));
        addLog("INSTRUMENTATION LOG - " + jobName);
        addLog("Job Execution ID: " + jobExecutionId);
        addLog("Started at: " + LocalDateTime.now().format(TIMESTAMP_FORMAT));
        addLog("=".repeat(80));
        addLog("");
        
        logger.info("In-memory instrumentation initialized for job: {}", jobName);
    }
    
    @Override
    public InstrumentationTask getOrCreateTask(String name, String description) {
        InstrumentationTask task = tasksByName.get(name);
        
        if (task == null) {
            task = new InstrumentationTask(UUID.randomUUID().toString(), name, description);
            tasksByName.put(name, task);
            
            addLog("[TASK CREATED] " + name);
            addLog("  Description: " + description);
            addLog("  Task ID: " + task.getId());
            addLog("");
        }
        
        return task;
    }
    
    @Override
    public TaskExecutionHandle startTask(String taskId) {
        String executionId = UUID.randomUUID().toString();
        TaskExecutionHandle response = new TaskExecutionHandle(executionId);
        
        taskStartTimes.put(executionId, System.currentTimeMillis());
        
        InstrumentationTask task = findTaskById(taskId);
        String taskName = task != null ? task.getName() : taskId;
        
        addLog("[TASK STARTED] " + taskName);
        addLog("  Task Execution ID: " + executionId);
        addLog("  Started at: " + LocalDateTime.now().format(TIMESTAMP_FORMAT));
        addLog("");
        
        return response;
    }
    
    @Override
    public void endTask(String taskExecutionId, String exceptionMessage) {
        Long startTime = taskStartTimes.remove(taskExecutionId);
        long duration = startTime != null ? System.currentTimeMillis() - startTime : 0;
        
        InstrumentationTask task = findTaskByExecutionId(taskExecutionId);
        String taskName = task != null ? task.getName() : taskExecutionId;
        
        addLog("[TASK ENDED] " + taskName);
        addLog("  Task Execution ID: " + taskExecutionId);
        addLog("  Ended at: " + LocalDateTime.now().format(TIMESTAMP_FORMAT));
        addLog("  Duration: " + duration + " ms (" + (duration / 1000.0) + " seconds)");
        
        if (exceptionMessage != null) {
            addLog("  Status: FAILED");
            addLog("  Error: " + exceptionMessage);
        } else {
            addLog("  Status: SUCCESS");
        }
        addLog("");
    }
    
    @Override
    public void addMessagesToTask(String executionId, List<String> messages) {
        InstrumentationTask task = findTaskByExecutionId(executionId);
        String taskName = task != null ? task.getName() : executionId;
        
        addLog("[TASK MESSAGES] " + taskName);
        for (String message : messages) {
            addLog("  - " + message);
        }
        addLog("");
    }
    
    @Override
    public void addStatisticsToTask(String executionId, Map<String, Object> statistics) {
        InstrumentationTask task = findTaskByExecutionId(executionId);
        String taskName = task != null ? task.getName() : executionId;
        
        addLog("[TASK STATISTICS] " + taskName);
        for (Map.Entry<String, Object> entry : statistics.entrySet()) {
            addLog("  " + entry.getKey() + ": " + entry.getValue());
        }
        addLog("");
    }
    
    @Override
    public void addStatisticsToJob(Map<String, Object> statistics) {
        addLog("[JOB STATISTICS]");
        for (Map.Entry<String, Object> entry : statistics.entrySet()) {
            addLog("  " + entry.getKey() + ": " + entry.getValue());
        }
        addLog("");
    }
    
    @Override
    public InstrumentationJob getJob() {
        return new InstrumentationJob(jobExecutionId, jobName);
    }
    
    @Override
    public Map<InstrumentationTask, TaskExecutionHandle> getRunningTasks() {
        return runningTasks;
    }
    
    @Override
    public String getRunningTaskExecutionId(String taskName) {
        return this.runningTasks.entrySet().stream()
                .filter(runningTask -> runningTask.getKey().getName().equals(taskName))
                .findFirst()
                .map(runningTask -> runningTask.getValue().getTaskExecutionId())
                .orElse(null);
    }
    
    private void addLog(String message) {
        logEntries.add(message);
    }
    
    private InstrumentationTask findTaskById(String taskId) {
        return tasksByName.values().stream()
                .filter(task -> task.getId().equals(taskId))
                .findFirst()
                .orElse(null);
    }
    
    private InstrumentationTask findTaskByExecutionId(String executionId) {
        return runningTasks.entrySet().stream()
                .filter(entry -> entry.getValue().getTaskExecutionId().equals(executionId))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Output all stored logs to the logger.
     */
    public void outputLogs() {
        addLog("");
        addLog("=".repeat(80));
        addLog("INSTRUMENTATION LOG ENDED");
        addLog("Ended at: " + LocalDateTime.now().format(TIMESTAMP_FORMAT));
        addLog("=".repeat(80));
    }
}
