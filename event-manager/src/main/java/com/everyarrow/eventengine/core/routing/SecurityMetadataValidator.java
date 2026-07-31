package com.everyarrow.eventengine.core.routing;

import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.eventengine.core.security.SecurityApplicationTargets;
import com.everyarrow.eventengine.core.services.registry.ServiceRegistry;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.everyarrow.utilities.environment.Environment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Warn-only validation for security-relevant route metadata.
 */
public final class SecurityMetadataValidator {

    private SecurityMetadataValidator() {}

    public static void validate(Engine engine) {
        if (engine == null) {
            throw new IllegalArgumentException("engine is required");
        }

        ServiceRegistry registry = engine.getRegistry();
        Collection<ServiceCallDefinition> defs = registry.listDefinitions();
        Environment environment = Environment.getInstance();
        Map<String, List<String>> routesByMissingEnv = new LinkedHashMap<>();
        List<String> routesWithoutApplication = new ArrayList<>();

        for (ServiceCallDefinition def : defs) {
            if (def == null || !def.isActive() || !hasExternalRoute(def) || isPublic(def)) {
                continue;
            }

            String serviceType = def.getServiceType();
            String applicationKey = resolveApplicationKey(def);
            if (isBlank(applicationKey)) {
                routesWithoutApplication.add(safeServiceType(serviceType));
                continue;
            }

            String expectedEnvVar = SecurityApplicationTargets.expectedConfigDescription(applicationKey, environment);
            String consoleId = SecurityApplicationTargets.resolveConsoleId(applicationKey, environment);

            if (isBlank(consoleId)) {
                routesByMissingEnv
                        .computeIfAbsent(expectedEnvVar, ignored -> new ArrayList<>())
                        .add(safeServiceType(serviceType));
            }
        }

        warnRoutes(routesWithoutApplication,
                "no call.applicationKey, monitored.application metadata, or service namespace could be resolved");
        for (Map.Entry<String, List<String>> entry : routesByMissingEnv.entrySet()) {
            warnRoutes(entry.getValue(), "missing target console id env " + entry.getKey());
        }
    }

    private static boolean hasExternalRoute(ServiceCallDefinition def) {
        return def.hasRestRoute() || def.hasWebhook() || def.hasWebSocketRoute();
    }

    private static boolean isPublic(ServiceCallDefinition def) {
        return def.getAnnotations() != null
                && ((def.getAnnotations().secured != null && def.getAnnotations().secured.isPublic)
                || (def.getAnnotations().call != null && def.getAnnotations().call.open));
    }

    private static String resolveApplicationKey(ServiceCallDefinition def) {
        if (def.getAnnotations() != null
                && def.getAnnotations().call != null
                && !isBlank(def.getAnnotations().call.applicationKey)) {
            return SecurityApplicationTargets.normalizeApplicationKey(def.getAnnotations().call.applicationKey);
        }

        if (def.getAnnotations() != null
                && def.getAnnotations().monitored != null
                && !isBlank(def.getAnnotations().monitored.application)) {
            return SecurityApplicationTargets.normalizeApplicationKey(def.getAnnotations().monitored.application);
        }

        String serviceType = def.getServiceType();
        if (isBlank(serviceType)) {
            return null;
        }

        int separatorIndex = serviceType.indexOf('.');
        String namespace = separatorIndex >= 0 ? serviceType.substring(0, separatorIndex) : serviceType;
        return SecurityApplicationTargets.normalizeApplicationKey(namespace);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void warnRoutes(List<String> serviceTypes, String reason) {
        if (serviceTypes.isEmpty()) {
            return;
        }

        int count = serviceTypes.size();
        String routeLabel = count == 1 ? "route" : "routes";
        String examples = serviceTypes.stream()
                .limit(3)
                .collect(Collectors.joining(", "));
        String suffix = count > 3 ? ", ..." : "";
        System.err.printf(
                "Warning: %d secured external %s may fail authorization at runtime: %s. Examples: %s%s.%n",
                count,
                routeLabel,
                reason,
                examples,
                suffix);
    }

    private static String safeServiceType(String serviceType) {
        return isBlank(serviceType) ? "unknown" : serviceType;
    }
}
