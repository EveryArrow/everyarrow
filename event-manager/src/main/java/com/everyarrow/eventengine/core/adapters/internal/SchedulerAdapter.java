package com.everyarrow.eventengine.core.adapters.internal;

import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.eventengine.core.adapters.ProtocolAdapter;
import com.everyarrow.eventengine.core.services.runtime.EventManagerServiceIdentity;
import com.everyarrow.eventengine.core.services.runtime.ServiceCall;
import com.everyarrow.eventengine.core.services.runtime.ServiceCallSource;
import com.everyarrow.eventengine.spi.model.ServiceCallResponse;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition.CalendarJobMeta;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

import java.util.List;
import java.util.Properties;
import java.util.TimeZone;

/**
 * Scheduler adapter using Quartz.
 * Discovers @CalendarJob metadata on service definitions and
 * schedules Quartz jobs that invoke the corresponding service type
 * via Engine.execute(ServiceCall).
 */
public class SchedulerAdapter implements ProtocolAdapter {

    private Scheduler scheduler;
    private Engine engine;

    @Override
    public void start(Engine engine) {
        if (!isEnabled()) {
            System.out.println("SchedulerAdapter disabled by EA_SCHEDULER_ENABLED=false");
            return;
        }

        try {
            // Keep engine on the adapter instance
            this.engine = engine;

            scheduler = createScheduler();
            scheduler.start();

            // Discover calendar jobs from the registry
            for (ServiceCallDefinition def : engine.getRegistry().listDefinitions()) {
                if (def.getAnnotations() == null ||
                        def.getAnnotations().calendarJobs == null ||
                        def.getAnnotations().calendarJobs.isEmpty()) {
                    continue;
                }

                List<CalendarJobMeta> jobs = def.getAnnotations().calendarJobs;
                for (CalendarJobMeta meta : jobs) {
                    registerScheduledJob(def, meta);
                }
            }

            System.out.println("Quartz Scheduler Adapter started.");
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to start SchedulerAdapter", e);
        }
    }

    @Override
    public void stop() {
        if (scheduler != null) {
            try {
                scheduler.shutdown(true);
            } catch (SchedulerException e) {
                System.err.println("Error shutting down Quartz scheduler: " + e.getMessage());
                e.printStackTrace(System.err);
            }
        }
    }

    // ---------------------------------------------------------------------
    // Scheduler creation
    // ---------------------------------------------------------------------

    private Scheduler createScheduler() throws Exception {
        Properties props = new Properties();

        String mode = System.getenv().getOrDefault("EE_SCHEDULER_MODE", "memory");

        switch (mode) {
            case "database" -> configureDatabaseScheduler(props);
            case "json"     -> configureJsonScheduler(props);
            default         -> configureMemoryScheduler(props);
        }

        StdSchedulerFactory factory = new StdSchedulerFactory(props);
        return factory.getScheduler();
    }

    private void configureMemoryScheduler(Properties props) {
        props.setProperty("org.quartz.scheduler.instanceName", "EventEngineRAMScheduler");
        props.setProperty("org.quartz.threadPool.threadCount", "3");
        props.setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");
    }

    private void configureDatabaseScheduler(Properties props) {
        props.setProperty("org.quartz.scheduler.instanceName", "EventEngineDBScheduler");
        props.setProperty("org.quartz.threadPool.threadCount", "5");

        props.setProperty("org.quartz.jobStore.class", "org.quartz.impl.jdbcjobstore.JobStoreTX");
        props.setProperty("org.quartz.jobStore.driverDelegateClass",
                "org.quartz.impl.jdbcjobstore.StdJDBCDelegate");

        props.setProperty("org.quartz.jobStore.dataSource", "myDS");
        props.setProperty("org.quartz.dataSource.myDS.driver",   System.getenv("EE_DB_DRIVER"));
        props.setProperty("org.quartz.dataSource.myDS.URL",      System.getenv("EE_DB_URL"));
        props.setProperty("org.quartz.dataSource.myDS.user",     System.getenv("EE_DB_USER"));
        props.setProperty("org.quartz.dataSource.myDS.password", System.getenv("EE_DB_PASS"));
    }

    private void configureJsonScheduler(Properties props) {
        configureMemoryScheduler(props);

        props.setProperty("org.quartz.plugin.jobInitializer.class",
                "org.quartz.plugins.xml.XMLSchedulingDataProcessorPlugin");

        props.setProperty("org.quartz.plugin.jobInitializer.fileNames",
                System.getenv().getOrDefault("EE_SCHEDULER_CONFIG", "quartz_jobs.xml"));
    }

    // ---------------------------------------------------------------------
    // Job registration
    // ---------------------------------------------------------------------

    private void registerScheduledJob(ServiceCallDefinition def,
                                      CalendarJobMeta meta) throws Exception {

        String serviceType = def.getServiceType();
        if (serviceType == null || serviceType.isBlank()) {
            return;
        }

        JobDataMap data = new JobDataMap();
        data.put("serviceType", serviceType);
        data.put("engine", this.engine);  // pass engine into the job

        JobKey jobKey = JobKey.jobKey(meta.id);
        // Remove any pre-existing job with the same ID (handles engine restarts
        // and tests that boot multiple Engine instances in the same JVM).
        if (scheduler.checkExists(jobKey)) {
            scheduler.deleteJob(jobKey);
        }

        JobDetail job = JobBuilder.newJob(EngineInvokingJob.class)
                .withIdentity(meta.id)
                .usingJobData(data)
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(meta.id + "-trigger")
                .withSchedule(
                        CronScheduleBuilder
                                .cronSchedule(meta.cron)
                                .inTimeZone(TimeZone.getTimeZone(meta.timezone))
                )
                .build();

        scheduler.scheduleJob(job, trigger);
        if (meta.startup) {
            executeStartupJob(serviceType, meta);
        }

        System.out.printf("Registered job: %s (%s, %s)%n",
                serviceType, meta.methodName, meta.cron);
    }

    private void executeStartupJob(String serviceType, CalendarJobMeta meta) throws Exception {
        ServiceCall call = newScheduledServiceCall(engine, serviceType);
        ServiceCallResponse response = engine.execute(call);
        System.out.printf(
                "Executed calendar startup service: %s (%s) status=%d bodyType=%s bodyLength=%d%n",
                serviceType,
                meta.methodName,
                response.getHttpStatus(),
                response.getBody() == null ? "null" : response.getBody().getClass().getSimpleName(),
                response.getBody() == null ? 0 : response.getBody().toString().length());
    }

    static ServiceCall newScheduledServiceCall(Engine engine, String serviceType) {
        ServiceCall call = new ServiceCall(engine);
        call.setType(serviceType);
        call.setSource(ServiceCallSource.SCHEDULE);
        new EventManagerServiceIdentity().applyCredential(call);
        return call;
    }

    private static boolean isEnabled() {
        String configured = System.getProperty("EA_SCHEDULER_ENABLED");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv().getOrDefault("EA_SCHEDULER_ENABLED", "true");
        }
        return Boolean.parseBoolean(configured);
    }

    // ---------------------------------------------------------------------
    // Quartz job wrapper
    // ---------------------------------------------------------------------

    public static class EngineInvokingJob implements Job {
        @Override
        public void execute(JobExecutionContext ctx) throws JobExecutionException {
            try {
                JobDataMap data = ctx.getMergedJobDataMap();
                String serviceType = data.getString("serviceType");
                Engine engine = (Engine) data.get("engine");

                if (engine == null) {
                    throw new JobExecutionException("Engine is not available in job data");
                }

                ServiceCall call = newScheduledServiceCall(engine, serviceType);
                engine.execute(call);
            }
            catch (Exception ex) {
                throw new JobExecutionException(ex);
            }
        }
    }
}
