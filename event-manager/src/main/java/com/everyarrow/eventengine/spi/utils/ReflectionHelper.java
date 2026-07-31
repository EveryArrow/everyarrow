package com.everyarrow.eventengine.spi.utils;

import com.everyarrow.eventengine.spi.annotations.Monitored;
import com.everyarrow.eventengine.spi.annotations.RestRoute;
import com.everyarrow.eventengine.spi.annotations.Secured;
import com.everyarrow.eventengine.spi.annotations.Webhook;
import com.everyarrow.eventengine.spi.annotations.CalendarJob;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition.MonitoredMeta;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition.RestRouteMeta;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition.SecuredMeta;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition.WebhookMeta;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition.CalendarJobMeta;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;

public class ReflectionHelper {

    public static ServiceCallDefinition extractDefinitionFromAnnotations(Class<?> clazz) {
        Annotation[] annotations = clazz.getAnnotations();
        if (annotations.length == 0) return null;

        ServiceCallDefinition def = new ServiceCallDefinition();
        def.setClassName(clazz.getName());
        def.setActive(true);
        def.setCategory(determineCategory(clazz));
        def.setServiceType(extractServiceType(clazz));
        def.setAnnotations(new ServiceCallDefinition.Annotations());

        // Inspect all annotations on the class once
        for (Annotation ann : annotations) {
            if (ann instanceof Monitored monitored) {
                def.setMonitoredAnnotation(monitored);
                def.getAnnotations().monitored = extractMonitored(monitored);
            } else if (ann instanceof Secured secured) {
                def.setSecuredAnnotation(secured);
                def.getAnnotations().secured = extractSecured(secured);
            } else if (ann instanceof RestRoute restRoute) {
                def.setRestRouteAnnotation(restRoute);
                def.getAnnotations().restRoute = extractRestRoute(restRoute);
            } else if (ann instanceof Webhook webhook) {
                def.setWebhookAnnotation(webhook);
                def.getAnnotations().webhook = extractWebhook(webhook);
            }
        }

        // Scan methods for @CalendarJob
        for (Method m : clazz.getDeclaredMethods()) {
            CalendarJob cj = m.getAnnotation(CalendarJob.class);
            if (cj == null) {
                continue;
            }
            if (def.getAnnotations().calendarJobs == null) {
                def.getAnnotations().calendarJobs = new ArrayList<>();
            }
            CalendarJobMeta meta = new CalendarJobMeta();
            meta.methodName = m.getName();
            meta.id = (cj.id() == null || cj.id().isBlank())
                    ? def.getServiceType() + "#" + m.getName()
                    : cj.id();
            meta.cron = cj.cron();
            meta.timezone = cj.timezone();
            def.getAnnotations().calendarJobs.add(meta);
        }


        // Only register if at least one relevant annotation is present
        if (def.getAnnotations().monitored != null ||
                def.getAnnotations().secured != null ||
                def.getAnnotations().restRoute != null ||
                def.getAnnotations().webhook != null) {
            return def;
        }

        return null;
    }

    private static String determineCategory(Class<?> clazz) {
        Package pkg = clazz.getPackage();
        if (pkg == null) return "Unknown";
        String p = pkg.getName();
        if (p.contains("rest")) return "REST";
        if (p.contains("webhook")) return "Webhook";
        if (p.contains("sqs") || p.contains("kafka")) return "Queue";
        if (p.contains("console")) return "Console";
        return "Generic";
    }

    private static String extractServiceType(Class<?> clazz) {
        String name = clazz.getSimpleName().replace("Handler", "");
        if (name.isEmpty()) return "unknown";
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    public static MonitoredMeta extractMonitored(Monitored ann) {
        try {
            MonitoredMeta m = new MonitoredMeta();
            m.application = ann.application();
            m.system = ann.system();
            m.description = ann.description();
            return m;
        } catch (Exception e) {
            return null;
        }
    }

    public static SecuredMeta extractSecured(Secured ann) {
        try {
            SecuredMeta s = new SecuredMeta();
            s.roles = Arrays.asList(ann.roles());
            s.scopes = Arrays.asList(ann.scopes());
            s.isPublic = ann.isPublic();
            return s;
        } catch (Exception e) {
            return null;
        }
    }

    public static RestRouteMeta extractRestRoute(RestRoute ann) {
        try {
            RestRouteMeta r = new RestRouteMeta();
            r.path = ann.path();
            r.method = ann.method();
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    public static WebhookMeta extractWebhook(Webhook ann) {
        try {
            WebhookMeta w = new WebhookMeta();
            w.path = ann.path();
            return w;
        } catch (Exception e) {
            return null;
        }
    }

}
