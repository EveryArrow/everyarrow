package com.everyarrow.eventengine.core.services.registry;

import com.everyarrow.eventengine.core.services.runtime.GenericProcessHandler;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.everyarrow.utilities.environment.Environment;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Guardrails for database-backed service registrations before they become executable.
 */
public final class DbServiceRegistrationPolicy {

    private static final String ALLOWLIST_CONFIG = "EA_DB_SERVICE_ALLOWLIST";
    private static final String LENIENT_ROUTE_SECURITY_CONFIG = "EA_LOCAL_DEV_LENIENT_DB_ROUTE_SECURITY";
    private static final Set<String> DESTRUCTIVE_METHODS = Set.of("DELETE", "PATCH", "PUT");
    private static final Set<String> DESTRUCTIVE_PREFIXES = Set.of(
            "delete_", "drop_", "truncate_", "remove_", "update_", "patch_", "replace_");

    private DbServiceRegistrationPolicy() {
    }

    public static void validate(ServiceCallDefinition definition, String sourceName) {
        if (!isDbBacked(definition)) {
            return;
        }

        validateProvenance(definition, sourceName);
        validateAllowlist(definition, sourceName);
        validateRouteSecurity(definition, sourceName);
    }

    private static void validateProvenance(ServiceCallDefinition definition, String sourceName) {
        String serviceType = definition.getServiceType();
        String source = definition.getSource();
        if (isBlank(serviceType)) {
            throw new IllegalStateException("DB service registry entry is missing serviceType in " + sourceName);
        }
        if (isBlank(source) || !source.startsWith("db:")) {
            throw new IllegalStateException("DB service '" + serviceType + "' is missing db: source provenance in " + sourceName);
        }

        String sourceDatabase = source.substring(3);
        if (isBlank(sourceDatabase)) {
            throw new IllegalStateException("DB service '" + serviceType + "' is missing database provenance in " + sourceName);
        }
    }

    private static void validateAllowlist(ServiceCallDefinition definition, String sourceName) {
        Set<String> allowlist = configuredAllowlist();
        if (allowlist.isEmpty()) {
            return;
        }

        String serviceType = definition.getServiceType();
        boolean allowed = allowlist.contains(serviceType)
                || allowlist.stream().anyMatch(pattern -> wildcardMatches(pattern, serviceType));
        if (!allowed) {
            throw new IllegalStateException("DB service '" + serviceType + "' is not in " + ALLOWLIST_CONFIG
                    + " for " + sourceName);
        }
    }

    private static void validateRouteSecurity(ServiceCallDefinition definition, String sourceName) {
        if (!hasExternalRoute(definition)) {
            return;
        }

        ServiceCallDefinition.Annotations annotations = definition.getAnnotations();
        ServiceCallDefinition.CallMeta callMeta = annotations == null ? null : annotations.call;
        if (callMeta == null || isBlank(callMeta.applicationKey)) {
            if (allowMissingApplicationKeyInLocalDev()) {
                System.out.println("Warning: DB route '" + definition.getServiceType()
                        + "' is missing annotations.call.applicationKey in " + sourceName
                        + " but local dev lenient route security is enabled");
                return;
            }
            throw new IllegalStateException("DB route '" + definition.getServiceType()
                    + "' must declare annotations.call.applicationKey in " + sourceName);
        }

        if (isPublic(definition) && isDestructive(definition)) {
            throw new IllegalStateException("Destructive DB route '" + definition.getServiceType()
                    + "' cannot be public in " + sourceName);
        }
    }

    private static Set<String> configuredAllowlist() {
        String configured = Environment.getInstance().get(ALLOWLIST_CONFIG, "");
        if (isBlank(configured)) {
            return Set.of();
        }
        return Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static boolean wildcardMatches(String pattern, String serviceType) {
        if (isBlank(pattern) || isBlank(serviceType) || !pattern.endsWith(".*")) {
            return false;
        }
        String prefix = pattern.substring(0, pattern.length() - 1);
        return serviceType.startsWith(prefix);
    }

    private static boolean hasExternalRoute(ServiceCallDefinition definition) {
        return definition.hasRestRoute() || definition.hasWebhook() || definition.hasWebSocketRoute();
    }

    private static boolean isPublic(ServiceCallDefinition definition) {
        ServiceCallDefinition.Annotations annotations = definition.getAnnotations();
        if (annotations == null) {
            return false;
        }
        return (annotations.call != null && annotations.call.open)
                || (annotations.secured != null && annotations.secured.isPublic);
    }

    private static boolean isDestructive(ServiceCallDefinition definition) {
        if (definition.hasRestRoute()
                && definition.getRestRoute().method != null
                && DESTRUCTIVE_METHODS.contains(definition.getRestRoute().method.trim().toUpperCase())) {
            return true;
        }

        String function = functionName(definition.getServiceType());
        return DESTRUCTIVE_PREFIXES.stream().anyMatch(function::startsWith);
    }

    private static String functionName(String serviceType) {
        if (isBlank(serviceType)) {
            return "";
        }
        String[] parts = serviceType.split("\\.");
        return parts.length == 0 ? "" : parts[parts.length - 1].toLowerCase();
    }

    private static boolean isDbBacked(ServiceCallDefinition definition) {
        if (definition == null) {
            return false;
        }
        String handlerClass = firstNonBlank(definition.getHandlerClass(), definition.getClassName());
        return GenericProcessHandler.class.getSimpleName().equals(handlerClass)
                || GenericProcessHandler.class.getName().equals(handlerClass)
                || (!isBlank(definition.getSource()) && definition.getSource().startsWith("db:"));
    }

    private static String firstNonBlank(String first, String second) {
        return isBlank(first) ? second : first;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean allowMissingApplicationKeyInLocalDev() {
        return Boolean.parseBoolean(Environment.getInstance().get(LENIENT_ROUTE_SECURITY_CONFIG, "false"));
    }
}
