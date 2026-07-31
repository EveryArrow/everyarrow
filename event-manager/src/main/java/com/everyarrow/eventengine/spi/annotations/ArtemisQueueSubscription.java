package com.everyarrow.eventengine.spi.annotations;

import java.lang.annotation.*;

/**
 * Declares that a handler method is subscribed to an Artemis queue destination.
 *
 * This annotation only declares subscription metadata. Dispatch still occurs by serviceType through the registry.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ArtemisQueueSubscription {

    /** Queue name or address. */
    String destination();

    /** Max retries before dead letter. Default is 3. */
    int maxRetries() default 3;

    /** Optional JMS selector used by shared queues to route messages by properties. */
    String selector() default "";

    String applicationKey() default "";

    boolean open() default false;

    String perchApplicationName() default "";

    String perchSystemName() default "";

    String perchDescription() default "";
}





/*package com.everyarrow.eventengine.spi.annotations;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface ArtemisQueueSubscription {
    String destination();
    int maxRetries() default 3;
    boolean enabled() default true;
    String selector() default "";
    String id() default "";
}
*/

