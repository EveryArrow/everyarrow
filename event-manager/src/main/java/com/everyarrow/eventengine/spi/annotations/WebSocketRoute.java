package com.everyarrow.eventengine.spi.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a Socket.IO event name for a service handler method.
 *
 * <p>The {@code path} name is retained for registry compatibility.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface WebSocketRoute {
    String path();

    String applicationKey() default "";

    boolean open() default false;

    String perchApplicationName() default "";

    String perchSystemName() default "";

    String perchDescription() default "";
}
