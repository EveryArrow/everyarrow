package com.everyarrow.eventengine.tests;

import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.eventengine.core.adapters.AnnotationDefinitionBuilder;
import com.everyarrow.eventengine.tests.eventtype.CalendarJobTestService;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

import java.util.Collection;

/**
 * Lightweight Quartz bootstrap used only in tests.
 *
 * It:
 *  - injects CalendarJobTestService into the engine registry (in case it
 *    isn't discovered via the normal plugin scan in tests)
 *  - walks all ServiceCallDefinitions and schedules each calendar job
 *    exactly once, immediately
 *  - invokes the underlying job method via reflection
 *
 * This avoids needing real cron expressions to fire during tests and keeps
 * the behaviour simple and deterministic.
 */
public class TestSchedulerBootstrap {

    private Scheduler scheduler;

    public void start(Engine engine) throws Exception {

        // 1. Force engine to register the test service definition
        ServiceCallDefinition injected =
                AnnotationDefinitionBuilder.buildDefinition(CalendarJobTestService.class);

        if (injected != null) {
            engine.getRegistry().registerDefinition(injected);
            System.out.println("[TestSchedulerBootstrap] Registered test service: "
                    + injected.getServiceType());
        }

        // 2. Start Quartz
        scheduler = StdSchedulerFactory.getDefaultScheduler();
        scheduler.start();

        // 3. Walk definitions for calendar jobs and schedule each once
        Collection<ServiceCallDefinition> defs = engine.getRegistry().listDefinitions();

        for (ServiceCallDefinition def : defs) {
            if (!def.hasCalendarJobs()) continue;

            for (ServiceCallDefinition.CalendarJobMeta job : def.getCalendarJobs()) {
                scheduleTestJob(def, job);
            }
        }
    }

    // ---------------------------------------------------------------------

    private void scheduleTestJob(
            ServiceCallDefinition def,
            ServiceCallDefinition.CalendarJobMeta job
    ) throws Exception {

        String id = def.getServiceType() + "#" + job.methodName;

        JobDetail detail = JobBuilder.newJob(InvokeCalendarJob.class)
                .withIdentity(id, "calendar-test")
                .usingJobData("declaringClass", job.declaringClass)
                .usingJobData("methodName", job.methodName)
                .build();

        // Fire within 1 second, exactly once
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(id + "-trigger", "calendar-test")
                .startNow()
                .withSchedule(SimpleScheduleBuilder
                        .simpleSchedule()
                        .withIntervalInSeconds(1)
                        .withRepeatCount(0)
                        .withMisfireHandlingInstructionFireNow())
                .build();

        scheduler.scheduleJob(detail, trigger);

        System.out.println("[TestSchedulerBootstrap] Scheduled calendar test fire for: " + id);
    }

    // ---------------------------------------------------------------------

    public static class InvokeCalendarJob implements Job {
        @Override
        public void execute(JobExecutionContext ctx) {
            try {
                JobDataMap map = ctx.getMergedJobDataMap();

                String className = map.getString("declaringClass");
                String methodName = map.getString("methodName");

                Class<?> clazz = Class.forName(className);
                Object instance = clazz.getDeclaredConstructor().newInstance();

                clazz.getMethod(methodName).invoke(instance);

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    // ---------------------------------------------------------------------

    public void shutdown() throws Exception {
        if (scheduler != null) scheduler.shutdown(true);
    }
}