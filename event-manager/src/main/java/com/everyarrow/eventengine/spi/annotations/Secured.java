package com.everyarrow.eventengine.spi.annotations;

import java.lang.annotation.*;

/**
 * Declares security constraints for a handler.
 * SecurityHook enforces that the current user’s roles
 * match the specified allowed roles and scopes.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Secured {

    /** List of roles that can access this handler. */
    String[] roles() default {};

    /** Optional scope (e.g., "execute", "read", "write") */
    String[] scopes() default {"execute"};

    /** Whether this endpoint can be publicly accessible. */
    boolean isPublic() default false;
}
