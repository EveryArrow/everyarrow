package com.everyarrow.dittoe.pipelines.behaviors.instrumentation;

import com.everyarrow.dittoe.pipelines.execution.abstraction.BehaviorException;
import com.google.gson.Gson;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RunnerEventInstrumentationService implements InstrumentationService {
    public static final String EVENT_PREFIX = "EA_INSTRUMENTATION:";

    private final Gson gson = new Gson();
    private final Map<InstrumentationTask, TaskExecutionHandle> runningTasks = new ConcurrentHashMap<>();
    private final Map<String, InstrumentationTask> tasksByName = new ConcurrentHashMap<>();
    private InstrumentationJob job;

    @Override
    public void initialize(Map<String, String> properties) throws BehaviorException {
        String jobName = properties.getOrDefault("jobName", "pipeline");
        this.job = new InstrumentationJob(properties.get("jobId"), jobName);
    }

    @Override
    public InstrumentationTask getOrCreateTask(String name, String description) {
        return tasksByName.computeIfAbsent(
                name,
                taskName -> new InstrumentationTask(UUID.randomUUID().toString(), taskName, description));
    }

    @Override
    public TaskExecutionHandle startTask(String taskId) {
        InstrumentationTask task = findTaskById(taskId);
        TaskExecutionHandle handle = new TaskExecutionHandle(UUID.randomUUID().toString());
        if (task != null) {
            runningTasks.put(task, handle);
            emit("task_start", Map.of(
                    "taskName", task.getName(),
                    "description", task.getDescription() == null ? "" : task.getDescription()));
        }
        return handle;
    }

    @Override
    public void endTask(String taskExecutionId, String exceptionMessage) {
        InstrumentationTask task = findTaskByExecutionId(taskExecutionId);
        if (task == null) {
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskName", task.getName());
        if (exceptionMessage != null && !exceptionMessage.isBlank()) {
            payload.put("exception", exceptionMessage);
        }
        emit("task_end", payload);
        runningTasks.remove(task);
    }

    @Override
    public void addMessagesToTask(String executionId, List<String> messages) {
        InstrumentationTask task = findTaskByExecutionId(executionId);
        if (task == null || messages == null || messages.isEmpty()) {
            return;
        }
        emit("task_messages", Map.of(
                "taskName", task.getName(),
                "messages", messages));
    }

    @Override
    public void addStatisticsToTask(String executionId, Map<String, Object> statistics) {
        InstrumentationTask task = findTaskByExecutionId(executionId);
        if (task == null || statistics == null || statistics.isEmpty()) {
            return;
        }
        emit("task_statistics", Map.of(
                "taskName", task.getName(),
                "statistics", statistics));
    }

    @Override
    public void addStatisticsToJob(Map<String, Object> statistics) {
        if (statistics == null || statistics.isEmpty()) {
            return;
        }
        emit("job_statistics", Map.of("statistics", statistics));
    }

    @Override
    public InstrumentationJob getJob() {
        return job;
    }

    @Override
    public Map<InstrumentationTask, TaskExecutionHandle> getRunningTasks() {
        return runningTasks;
    }

    @Override
    public String getRunningTaskExecutionId(String taskName) {
        return runningTasks.entrySet().stream()
                .filter(entry -> taskName.equals(entry.getKey().getName()))
                .findFirst()
                .map(entry -> entry.getValue().getTaskExecutionId())
                .orElse(null);
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

    private void emit(String type, Map<String, Object> payload) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("payload", payload);
        System.out.println(EVENT_PREFIX + gson.toJson(event));
    }
}
