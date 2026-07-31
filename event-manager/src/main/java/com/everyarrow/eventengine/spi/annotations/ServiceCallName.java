package com.everyarrow.eventengine.spi.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Customizes the service type name for a ServiceCallHandler.
 * Can be placed on a class (one handler, one type) or on individual methods
 * (one handler class, multiple types — each method handles its own type).
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ServiceCallName {
    String value();

    String applicationKey() default "";

    boolean open() default false;

    String perchApplicationName() default "";

    String perchSystemName() default "";

    String perchDescription() default "";
}
