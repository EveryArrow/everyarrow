package com.everyarrow.dittoe.pipelines.behaviors.instrumentation;

import com.everyarrow.dittoe.pipelines.execution.abstraction.BehaviorException;

import java.util.List;
import java.util.Map;

public interface InstrumentationService {

    void initialize(Map<String, String> properties) throws BehaviorException;

    InstrumentationTask getOrCreateTask(String name, String description);

    TaskExecutionHandle startTask(String taskId);

    void endTask(String taskExecutionId, String exceptionMessage);

    void addMessagesToTask(String executionId, List<String> messages);

    void addStatisticsToTask(String executionId, Map<String, Object> statistics);

    void addStatisticsToJob(Map<String, Object> statistics);

    InstrumentationJob getJob();

    Map<InstrumentationTask, TaskExecutionHandle> getRunningTasks();

    String getRunningTaskExecutionId(String taskName);
}
