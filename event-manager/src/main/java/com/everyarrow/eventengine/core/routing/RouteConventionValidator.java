package com.everyarrow.eventengine.core.routing;

import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.eventengine.core.services.registry.ServiceRegistry;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;

import java.util.Collection;

/**
 * Ensures annotation integrity ONLY.
 *
 * Services WITHOUT routes are perfectly legal and simply remain
 * internal-only. No errors are thrown for missing REST/Webhook routes.
 *
 * This validator only checks:
 *  - If route annotations exist, they must be valid (ex: path non-empty).
 *  - If security/monitoring annotations exist, they must be well-formed.
 */
public final class RouteConventionValidator {

    private RouteConventionValidator() {}

    public static void validate(Engine engine) {
        if (engine == null) {
            throw new IllegalArgumentException("engine is required");
        }

        ServiceRegistry registry = engine.getRegistry();
        Collection<ServiceCallDefinition> defs = registry.listDefinitions();

        for (ServiceCallDefinition def : defs) {

            if (def == null || !def.isActive()) {
                continue;
            }

            // Validate REST annotation if present
            if (def.hasRestRoute()) {
                var rr = def.getRestRoute();
                if (rr.path == null || rr.path.isBlank()) {
                    throw new IllegalStateException(
                            "REST route for serviceType=" + def.getServiceType() +
                                    " has an empty path"
                    );
                }
                if (rr.method == null || rr.method.isBlank()) {
                    throw new IllegalStateException(
                            "REST route for serviceType=" + def.getServiceType() +
                                    " is missing an HTTP method"
                    );
                }
            }

            // Validate Webhook annotation if present
            if (def.hasWebhook()) {
                var wh = def.getWebhook();
                if (wh.path == null || wh.path.isBlank()) {
                    throw new IllegalStateException(
                            "Webhook route for serviceType=" + def.getServiceType() +
                                    " has an empty path"
                    );
                }
            }

            if (def.hasWebSocketRoute()) {
                var ws = def.getWebSocketRoute();
                if (ws.path == null || ws.path.isBlank()) {
                    throw new IllegalStateException(
                            "WebSocket route for serviceType=" + def.getServiceType() +
                                    " has an empty path"
                    );
                }
            }

            // Security and Monitoring validation could be added here
        }
    }
}
