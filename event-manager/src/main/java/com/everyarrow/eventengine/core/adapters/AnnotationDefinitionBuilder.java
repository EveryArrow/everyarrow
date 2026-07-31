package com.everyarrow.eventengine.core.adapters;

import com.everyarrow.eventengine.core.adapters.internal.ArtemisQueueAnnotationAdapter;
import com.everyarrow.eventengine.core.adapters.internal.CalendarJobAnnotationAdapter;
import com.everyarrow.eventengine.core.adapters.internal.FunctionContractAnnotationAdapter;
import com.everyarrow.eventengine.core.adapters.internal.MonitoredAnnotationAdapter;
import com.everyarrow.eventengine.core.adapters.internal.PostgresNotificationAnnotationAdapter;
import com.everyarrow.eventengine.core.adapters.external.http.RestRouteAnnotationAdapter;
import com.everyarrow.eventengine.core.adapters.external.websocket.WebSocketRouteAnnotationAdapter;
import com.everyarrow.eventengine.core.adapters.internal.SecuredAnnotationAdapter;
import com.everyarrow.eventengine.core.adapters.external.http.webhook.WebhookAnnotationAdapter;
import com.everyarrow.eventengine.spi.annotations.ServiceCallName;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Central builder that uses AnnotationAdapter instances to construct
 * ServiceCallDefinitions from a handler class.
 *
 * Supports two modes:
 *  1. Class-level @ServiceCallName  → one definition per class (existing behaviour)
 *  2. Method-level @ServiceCallName → one definition per annotated method, each
 *     carrying its own @RestRoute/@Webhook and a targetMethodName so Engine can
 *     invoke the specific method via reflection instead of execute().
 */
public final class AnnotationDefinitionBuilder {

    private static final List<AnnotationAdapter> ADAPTERS = List.of(
            new FunctionContractAnnotationAdapter(),
            new MonitoredAnnotationAdapter(),
            new SecuredAnnotationAdapter(),
            new RestRouteAnnotationAdapter(),
            new WebhookAnnotationAdapter(),
            new WebSocketRouteAnnotationAdapter(),
            new CalendarJobAnnotationAdapter(),
            new ArtemisQueueAnnotationAdapter(),
            new PostgresNotificationAnnotationAdapter());

    public AnnotationDefinitionBuilder() {
        // utility
    }

    /**
     * Original single-definition build — kept for callers that expect exactly one def.
     * Returns null when the class has no relevant annotations.
     */
    public static ServiceCallDefinition buildDefinition(Class<?> clazz) {
        // If any method carries @ServiceCallName we use the multi-def path; return only
        // the first one here so existing callers don't break entirely, but the caller
        // should prefer buildDefinitions() to get all of them.
        List<ServiceCallDefinition> all = buildDefinitions(clazz);
        if (all.isEmpty()) return null;
        return all.get(0);
    }

    /**
     * Build all definitions from a class.
     *
     * - If methods carry @ServiceCallName, returns one def per such method.
     * - Otherwise falls back to the classic single-def-per-class behaviour.
     */
    public static List<ServiceCallDefinition> buildDefinitions(Class<?> clazz) {
        List<ServiceCallDefinition> results = new ArrayList<>();

        // --- Method-level @ServiceCallName scan ---
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.isBridge() || m.isSynthetic()) {
                continue;
            }
            ServiceCallName ann = m.getAnnotation(ServiceCallName.class);
            if (ann == null) continue;

            ServiceCallDefinition def = new ServiceCallDefinition();
            def.setClassName(clazz.getName());
            def.setActive(true);
            def.setCategory(determineCategory(clazz));
            def.setServiceType(ann.value());
            def.setTargetMethodName(m.getName());
            def.annotations = new ServiceCallDefinition.Annotations();
            CallableAnnotationMetadata.apply(ann, def);

            // Apply class-level adapters (security, monitoring, etc.)
            for (AnnotationAdapter adapter : ADAPTERS) {
                adapter.applyToClass(clazz, def);
            }

            // Apply method-level adapters only for THIS method
            for (AnnotationAdapter adapter : ADAPTERS) {
                adapter.applyToMethod(m, def);
            }

            results.add(def);
        }

        if (!results.isEmpty()) {
            return results;
        }

        // --- Classic class-level fallback ---
        ServiceCallDefinition def = new ServiceCallDefinition();
        def.setClassName(clazz.getName());
        def.setActive(true);
        def.setCategory(determineCategory(clazz));
        def.setServiceType(extractClassServiceType(clazz));
        def.annotations = new ServiceCallDefinition.Annotations();
        CallableAnnotationMetadata.apply(clazz.getAnnotation(ServiceCallName.class), def);

        for (AnnotationAdapter adapter : ADAPTERS) {
            adapter.applyToClass(clazz, def);
        }

        for (Method m : clazz.getDeclaredMethods()) {
            if (m.isBridge() || m.isSynthetic()) {
                continue;
            }
            for (AnnotationAdapter adapter : ADAPTERS) {
                adapter.applyToMethod(m, def);
            }
        }

        if (clazz.isAnnotationPresent(ServiceCallName.class) || def.hasAnnotations()) {
            results.add(def);
        }
        return results;
    }

    private static String determineCategory(Class<?> clazz) {
        Package pkg = clazz.getPackage();
        if (pkg == null)
            return "Unknown";
        String p = pkg.getName();
        if (p.contains("rest"))
            return "REST";
        if (p.contains("webhook"))
            return "Webhook";
        if (p.contains("websocket"))
            return "WebSocket";
        if (p.contains("sqs") || p.contains("kafka"))
            return "Queue";
        if (p.contains("console"))
            return "Console";
        return "Generic";
    }

    private static String extractClassServiceType(Class<?> clazz) {
        if (clazz.isAnnotationPresent(ServiceCallName.class)) {
            return clazz.getAnnotation(ServiceCallName.class).value();
        }

        String name = clazz.getSimpleName().replace("Handler", "");
        if (name.isEmpty())
            return "unknown";
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
