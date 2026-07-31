package com.everyarrow.dittoe.pipelines.behaviors.instrumentation;

import com.everyarrow.dittoe.pipelines.execution.abstraction.BehaviorException;
import com.everyarrow.dittoe.utility.Config;
import org.everyarrow.dittoe.schema.Behavior;
import org.everyarrow.dittoe.schema.BehaviorType;

import java.util.List;
import java.util.Map;

/**
 * Instrumentation behavior.
 */
public class Instrumentation extends Behavior {

    private static final ThreadLocal<Instrumentation> instrumentation = new ThreadLocal<>();
    private static final ThreadLocal<String> apiKey = new ThreadLocal<>();
    private final InstrumentationService service;

    public Instrumentation(String name, String description, Map<String, String> properties) throws BehaviorException {
        super(name, BehaviorType.Instrumentation, properties, description);
        this.service = serviceFor(Config.INSTRUMENTATION_SERVICE_TYPE);
        this.service.initialize(properties);
    }

    public static Instrumentation inMemory(String name, String description, Map<String, String> properties)
            throws BehaviorException {
        return new Instrumentation(name, description, properties, new InMemoryInstrumentationService());
    }

    private Instrumentation(String name, String description, Map<String, String> properties,
                            InstrumentationService service) throws BehaviorException {
        super(name, BehaviorType.Instrumentation, properties, description);
        this.service = service;
        this.service.initialize(properties);
    }

    private static InstrumentationService serviceFor(String serviceType) {
        if ("runner".equalsIgnoreCase(serviceType)) {
            return new RunnerEventInstrumentationService();
        }
        return new InMemoryInstrumentationService();
    }

    public static Instrumentation getInstance() {
        return instrumentation.get();
    }

    public static void setInstance(Instrumentation instrumentation) {
        Instrumentation.instrumentation.set(instrumentation);
    }

    public static String getApiKey() {
        return apiKey.get();
    }

    public static void setApiKey(String apiKey) {
        Instrumentation.apiKey.set(apiKey);
    }

    public InstrumentationTask getOrCreateTask(String name, String description) {
        return service.getOrCreateTask(name, description);
    }

    public InstrumentationJob getJob() {
        return service.getJob();
    }

    public Map<InstrumentationTask, TaskExecutionHandle> getRunningTasks() {
        return service.getRunningTasks();
    }

    public String getRunningTaskExecutionId(String taskName) {
        return service.getRunningTaskExecutionId(taskName);
    }

    public TaskExecutionHandle startTask(String taskId) {
        return service.startTask(taskId);
    }

    public void endTask(String taskExecutionId, String exceptionMessage) {
        service.endTask(taskExecutionId, exceptionMessage);
    }

    public void addMessagesToTask(String executionId, List<String> messages) {
        service.addMessagesToTask(executionId, messages);
    }

    public void addStatisticsToTask(String executionId, Map<String, Object> statistics) {
        service.addStatisticsToTask(executionId, statistics);
    }

    public void addStatisticsToJob(Map<String, Object> statistics) {
        service.addStatisticsToJob(statistics);
    }
    
    public InstrumentationService getService() {
        return service;
    }
}
