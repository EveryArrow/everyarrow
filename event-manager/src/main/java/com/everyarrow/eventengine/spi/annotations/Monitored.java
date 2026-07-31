package com.everyarrow.eventengine.spi.annotations;

import java.lang.annotation.*;

/**
 * Declares that a handler or service should be monitored
 * by the Perch observability system.
 *
 * Metadata here is used by MonitoringHook to emit telemetry.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Monitored {

    /** Logical application name, e.g., "EveryArrow", "Analytics" */
    String application();

    /** Logical subsystem name, e.g., "Console", "Perch", "Security" */
    String system();

    /** Optional description for contextual logging */
    String description() default "";
}
