package com.everyarrow.eventengine.spi.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a method should be invoked on a schedule.
 *
 * The schedule is expressed as a Quartz-style cron expression.
 * This is metadata only; the engine loads this into memory and a scheduler
 * (such as Quartz) can use it to trigger the associated service.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CalendarJob {

    /**
     * Optional stable identifier for this job.
     * If left blank, a default id based on serviceType + method name can be used.
     */
    String id() default "";

    /**
     * Quartz cron expression, e.g. "0 0 3 * * ?" for 3AM daily.
     */
    String cron();

    /**
     * Time zone id for schedule evaluation, e.g. "UTC", "America/New_York".
     */
    String timezone() default "UTC";

    boolean startup() default false;

    String applicationKey() default "";

    boolean open() default false;

    String perchApplicationName() default "";

    String perchSystemName() default "";

    String perchDescription() default "";
}
