package com.everyarrow.dittoe.pipelines.behaviors.instrumentation;

public class TaskExecutionHandle {
    private final String taskExecutionId;

    public TaskExecutionHandle(String taskExecutionId) {
        this.taskExecutionId = taskExecutionId;
    }

    public String getTaskExecutionId() {
        return taskExecutionId;
    }
}
