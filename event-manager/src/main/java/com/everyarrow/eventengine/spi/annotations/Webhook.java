package com.everyarrow.eventengine.spi.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Codex: Annotation marking webhook endpoints handled by the engine.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Webhook {
    String path();

    String applicationKey() default "";

    boolean open() default false;

    String perchApplicationName() default "";

    String perchSystemName() default "";

    String perchDescription() default "";
}
