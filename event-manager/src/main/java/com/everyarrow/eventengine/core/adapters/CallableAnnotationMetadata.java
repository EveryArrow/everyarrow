package com.everyarrow.eventengine.core.adapters;

import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * Extracts shared callable policy fields from annotations that cannot share a Java base type.
 */
public final class CallableAnnotationMetadata {

    private CallableAnnotationMetadata() {
    }

    public static void apply(Annotation annotation, ServiceCallDefinition def) {
        if (annotation == null || def == null) {
            return;
        }

        String applicationKey = stringAttribute(annotation, "applicationKey");
        boolean open = booleanAttribute(annotation, "open");
        if (!isBlank(applicationKey) || open) {
            ServiceCallDefinition.CallMeta call = ensureCall(def);
            if (!isBlank(applicationKey)) {
                call.applicationKey = applicationKey.trim();
            }
            if (open) {
                call.open = true;
            }
        }

        String perchApplicationName = stringAttribute(annotation, "perchApplicationName");
        String perchSystemName = stringAttribute(annotation, "perchSystemName");
        String perchDescription = stringAttribute(annotation, "perchDescription");
        if (!isBlank(perchApplicationName) || !isBlank(perchSystemName) || !isBlank(perchDescription)) {
            ServiceCallDefinition.PerchMeta perch = ensurePerch(def);
            if (!isBlank(perchApplicationName)) {
                perch.applicationName = perchApplicationName.trim();
            }
            if (!isBlank(perchSystemName)) {
                perch.systemName = perchSystemName.trim();
            }
            if (!isBlank(perchDescription)) {
                perch.description = perchDescription.trim();
            }
        }
    }

    private static ServiceCallDefinition.CallMeta ensureCall(ServiceCallDefinition def) {
        if (def.getAnnotations().call == null) {
            def.getAnnotations().call = new ServiceCallDefinition.CallMeta();
        }
        return def.getAnnotations().call;
    }

    private static ServiceCallDefinition.PerchMeta ensurePerch(ServiceCallDefinition def) {
        if (def.getAnnotations().perch == null) {
            def.getAnnotations().perch = new ServiceCallDefinition.PerchMeta();
        }
        return def.getAnnotations().perch;
    }

    private static String stringAttribute(Annotation annotation, String name) {
        try {
            Method method = annotation.annotationType().getMethod(name);
            Object value = method.invoke(annotation);
            if (value == null) {
                return null;
            }
            String asString = value.toString();
            return asString.isBlank() ? null : asString;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static boolean booleanAttribute(Annotation annotation, String name) {
        try {
            Method method = annotation.annotationType().getMethod(name);
            Object value = method.invoke(annotation);
            return value instanceof Boolean bool && bool;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
