package com.everyarrow.eventengine.core.hooks;

import com.everyarrow.eventengine.core.services.runtime.ServiceCall;

/**
 * Cross-cutting hooks around service execution.
 */
public interface ServiceHook {

    /**
     * Called before the handler executes.
     * Throwing aborts handler execution.
     */
    void before(ServiceCall call) throws Exception;

    /**
     * Called after the handler finishes (success OR failure).
     * Exceptions are logged but do not affect the pipeline.
     */
    void after(ServiceCall call, Object result) throws Exception;

    /**
     * Called when handler.execute throws an exception.
     * This is for logging, metrics, security, etc.
     * Exceptions here should be logged and swallowed.
     */
    default void onException(ServiceCall call, Exception ex) throws Exception {
        // default no-op so existing hooks still compile
    }
}
