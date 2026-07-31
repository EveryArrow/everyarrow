package com.everyarrow.eventengine.core.adapters.external.http;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Immutable transport-level CORS policy snapshot.
 */
public class CorsPolicy {

    private final Set<String> allowedOrigins;
    private final boolean allowLoopbackOrigins;

    public CorsPolicy(Set<String> allowedOrigins, boolean allowLoopbackOrigins) {
        this.allowedOrigins = allowedOrigins == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(allowedOrigins));
        this.allowLoopbackOrigins = allowLoopbackOrigins;
    }

    public Set<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public boolean isAllowLoopbackOrigins() {
        return allowLoopbackOrigins;
    }

    public boolean allowsAnyOrigin() {
        return allowedOrigins.contains("*");
    }
}