package com.everyarrow.eventengine.core.adapters.external.http;

import com.everyarrow.utilities.environment.Environment;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves the active CORS policy for HTTP and websocket browser-facing ingress.
 *
 * This initial version preserves the existing engine behavior:
 * - loopback browser origins are always allowed
 * - EA_ALLOWED_CORS_ORIGINS provides an allow-list
 * - EA_ALLOW_ANY_CORS_ORIGIN must be explicitly true before "*" is honored
 */
public class CorsPolicyResolver {

    private static final Set<String> LOCALHOST_HOSTS = Set.of("localhost", "127.0.0.1", "::1");

    public CorsPolicy resolveDefaultPolicy() {
        String configured = Environment.getInstance().get("EA_ALLOWED_CORS_ORIGINS", "");
        Set<String> origins = configured == null || configured.isBlank()
                ? Set.of()
                : Arrays.stream(configured.split(","))
                .map(this::normalize)
                .filter(value -> !value.isEmpty())
                .filter(value -> !"*".equals(value) || allowAnyCorsOrigin())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return new CorsPolicy(origins, true);
    }

    public boolean isOriginAllowed(String origin) {
        return isOriginAllowed(origin, resolveDefaultPolicy());
    }

    public boolean isOriginAllowed(String origin, CorsPolicy policy) {
        String normalizedOrigin = normalize(origin);
        if (normalizedOrigin.isEmpty()) {
            return true;
        }

        if (policy != null && policy.isAllowLoopbackOrigins() && isLoopbackOrigin(normalizedOrigin)) {
            return true;
        }

        if (policy == null) {
            return false;
        }

        return policy.allowsAnyOrigin() || policy.getAllowedOrigins().contains(normalizedOrigin);
    }

    private boolean isLoopbackOrigin(String origin) {
        try {
            URI uri = URI.create(origin);
            String scheme = normalize(uri.getScheme());
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return false;
            }

            String host = normalize(uri.getHost())
                    .replace("[", "")
                    .replace("]", "");
            return LOCALHOST_HOSTS.contains(host);
        } catch (Exception ignored) {
            return false;
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean allowAnyCorsOrigin() {
        String configured = Environment.getInstance().get("EA_ALLOW_ANY_CORS_ORIGIN", "false");
        return "true".equalsIgnoreCase(normalize(configured));
    }
}
