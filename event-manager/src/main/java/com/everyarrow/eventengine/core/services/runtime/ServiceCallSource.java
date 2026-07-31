package com.everyarrow.eventengine.core.services.runtime;

/**
 * Identifies where a ServiceCall originated from.
 */
public enum ServiceCallSource {
    REST,
    WEBHOOK,
    WEBSOCKET,
    QUEUE,
    FILE,
    SCHEDULE,
    POSTGRES_NOTIFICATION,
    INTERNAL
}
