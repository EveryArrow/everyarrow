package com.everyarrow.eventengine.spi.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Codex: Annotation declaring REST endpoints for handlers.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RestRoute {
    String path();

    String method();

    String applicationKey() default "";

    boolean open() default false;

    String perchApplicationName() default "";

    String perchSystemName() default "";

    String perchDescription() default "";
}
