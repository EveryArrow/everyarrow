/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.registry;

import com.everyarrow.eventengine.core.adapters.AnnotationDefinitionBuilder;
import com.everyarrow.eventengine.core.adapters.CallableAnnotationMetadata;
import com.everyarrow.eventengine.spi.annotations.RestRoute;
import com.everyarrow.eventengine.spi.annotations.ServiceCallName;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.google.gson.Gson;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class DbAnnotationMetadataProjection {

    private static final Gson GSON = new Gson();
    private static final String DEFAULT_SELECTOR_COLUMNS = "[]";
    private static final Set<String> REGISTRY_ANNOTATIONS = Set.of(
            "ServiceCallName",
            "RestRoute",
            "Secured",
            "Monitored",
            "CalendarJob",
            "ArtemisQueueSubscription",
            "PostgresNotification",
            "Webhook",
            "WebSocketRoute");

    private DbAnnotationMetadataProjection() {
    }

    static List<DbServiceRegistryProjection> fromClasses(Collection<Class<?>> classes,
                                                         Map<String, String> classSources,
                                                         String surfaceId) {
        if (classes == null || classes.isEmpty()) {
            return List.of();
        }

        List<Class<?>> sortedClasses = new ArrayList<>(classes);
        sortedClasses.sort(Comparator.comparing(Class::getName));

        List<DbServiceRegistryProjection> projections = new ArrayList<>();
        for (Class<?> clazz : sortedClasses) {
            if (clazz == null) {
                continue;
            }
            String source = sourceFor(clazz, classSources);
            addClassAnnotations(projections, clazz, source, surfaceId);
            addMethodAnnotations(projections, clazz, source, surfaceId);
        }
        return projections;
    }

    private static void addClassAnnotations(List<DbServiceRegistryProjection> projections,
                                            Class<?> clazz,
                                            String source,
                                            String surfaceId) {
        for (Annotation annotation : sortedAnnotations(clazz.getDeclaredAnnotations())) {
            if (!isRegistryAnnotation(annotation)) {
                continue;
            }
            projections.add(projection(clazz, null, annotation, source, surfaceId));
        }
    }

    private static void addMethodAnnotations(List<DbServiceRegistryProjection> projections,
                                             Class<?> clazz,
                                             String source,
                                             String surfaceId) {
        Method[] methods = clazz.getDeclaredMethods();
        List<Method> sortedMethods = new ArrayList<>(List.of(methods));
        sortedMethods.sort(Comparator.comparing(DbAnnotationMetadataProjection::methodSignature));
        for (Method method : sortedMethods) {
            for (Annotation annotation : sortedAnnotations(method.getDeclaredAnnotations())) {
                if (!isRegistryAnnotation(annotation)) {
                    continue;
                }
                projections.add(projection(clazz, method, annotation, source, surfaceId));
            }
        }
    }

    private static DbServiceRegistryProjection projection(Class<?> clazz,
                                                         Method method,
                                                         Annotation annotation,
                                                         String source,
                                                         String surfaceId) {
        if (annotation instanceof ServiceCallName serviceCallName) {
            return executableProjection(clazz, method, serviceCallName, source, surfaceId);
        }

        String annotationName = annotation.annotationType().getSimpleName();
        String declaringClass = clazz.getName();
        String methodName = method == null ? null : method.getName();
        String serviceType = serviceType(declaringClass, method, annotation);
        String json = metadataJson(clazz, method, annotation, source, surfaceId);
        RestRoute route = restRouteFor(clazz, method, annotation);
        String functionName = methodName == null ? clazz.getSimpleName() : methodName;

        DbServiceRegistryProjection projection = new DbServiceRegistryProjection(
                serviceType,
                DbServiceRegistryProjection.KIND_ANNOTATION_METADATA,
                "event_manager",
                "annotations",
                "registry_annotations",
                functionName,
                annotationName,
                null,
                DEFAULT_SELECTOR_COLUMNS,
                "json",
                route == null ? null : route.method(),
                route == null ? null : route.path(),
                json,
                annotationName + " metadata for " + declaringClass
                        + (methodName == null ? "" : "#" + methodName),
                source,
                false,
                declaringClass,
                methodName,
                "Annotation",
                pluginId(source),
                normalizeSurfaceId(surfaceId),
                sha256(json)
        );
        return projection;
    }

    private static DbServiceRegistryProjection executableProjection(Class<?> clazz,
                                                                     Method method,
                                                                     ServiceCallName serviceCallName,
                                                                     String source,
                                                                     String surfaceId) {
        String targetMethodName = method == null ? null : method.getName();
        ServiceCallDefinition definition = AnnotationDefinitionBuilder.buildDefinitions(clazz).stream()
                .filter(candidate -> serviceCallName.value().equals(candidate.getServiceType()))
                .filter(candidate -> targetMethodName == null
                        || targetMethodName.equals(candidate.getTargetMethodName()))
                .findFirst()
                .orElseGet(() -> fallbackExecutableDefinition(clazz, targetMethodName, serviceCallName));

        definition.setSource(source);
        return DbServiceRegistryProjection.from(definition, surfaceId)
                .orElseGet(() -> {
                    ServiceCallDefinition fallback = fallbackExecutableDefinition(
                            clazz, targetMethodName, serviceCallName);
                    fallback.setSource(source);
                    return DbServiceRegistryProjection.from(fallback, surfaceId)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Unable to project executable registry definition for "
                                            + fallback.getServiceType()));
                });
    }

    private static ServiceCallDefinition fallbackExecutableDefinition(Class<?> clazz,
                                                                      String targetMethodName,
                                                                      ServiceCallName serviceCallName) {
        ServiceCallDefinition definition = new ServiceCallDefinition();
        definition.setServiceType(serviceCallName.value());
        definition.setClassName(clazz.getName());
        definition.setHandlerClass(clazz.getName());
        definition.setTargetMethodName(targetMethodName);
        definition.setCategory("Annotation");
        definition.setActive(true);
        definition.setAnnotations(new ServiceCallDefinition.Annotations());
        CallableAnnotationMetadata.apply(serviceCallName, definition);
        if (definition.getAnnotations().call == null) {
            definition.getAnnotations().call = new ServiceCallDefinition.CallMeta();
        }
        return definition;
    }

    private static String serviceType(String declaringClass, Method method, Annotation annotation) {
        if (annotation instanceof ServiceCallName serviceCallName
                && serviceCallName.value() != null
                && !serviceCallName.value().isBlank()) {
            return serviceCallName.value();
        }

        String location = declaringClass
                + (method == null ? "" : "#" + methodSignature(method))
                + "@" + annotation.annotationType().getName();
        return "annotation." + sha256(location);
    }

    private static String metadataJson(Class<?> clazz,
                                       Method method,
                                       Annotation annotation,
                                       String source,
                                       String surfaceId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("kind", "annotation");
        metadata.put("annotation", annotation.annotationType().getSimpleName());
        metadata.put("annotationClass", annotation.annotationType().getName());
        metadata.put("declaringClass", clazz.getName());
        metadata.put("methodName", method == null ? null : method.getName());
        metadata.put("methodSignature", method == null ? null : methodSignature(method));
        metadata.put("source", source);
        metadata.put("surfaceId", normalizeSurfaceId(surfaceId));
        metadata.put("attributes", annotationAttributes(annotation));
        return GSON.toJson(metadata);
    }

    private static Map<String, Object> annotationAttributes(Annotation annotation) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        Method[] methods = annotation.annotationType().getDeclaredMethods();
        List<Method> sorted = new ArrayList<>(List.of(methods));
        sorted.sort(Comparator.comparing(Method::getName));
        for (Method method : sorted) {
            try {
                attributes.put(method.getName(), normalizeAnnotationValue(method.invoke(annotation)));
            } catch (ReflectiveOperationException e) {
                attributes.put(method.getName(), "<unavailable>");
            }
        }
        return attributes;
    }

    private static Object normalizeAnnotationValue(Object value) {
        if (value == null) {
            return null;
        }
        Class<?> type = value.getClass();
        if (!type.isArray()) {
            if (value instanceof Class<?> clazz) {
                return clazz.getName();
            }
            if (value instanceof Annotation nested) {
                return annotationAttributes(nested);
            }
            return value;
        }

        int length = Array.getLength(value);
        List<Object> values = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            values.add(normalizeAnnotationValue(Array.get(value, i)));
        }
        return values;
    }

    private static RestRoute restRouteFor(Class<?> clazz, Method method, Annotation annotation) {
        if (annotation instanceof RestRoute restRoute) {
            return restRoute;
        }
        if (method != null) {
            return method.getAnnotation(RestRoute.class);
        }
        for (Method candidate : clazz.getDeclaredMethods()) {
            RestRoute restRoute = candidate.getAnnotation(RestRoute.class);
            if (restRoute != null) {
                return restRoute;
            }
        }
        return null;
    }

    private static List<Annotation> sortedAnnotations(Annotation[] annotations) {
        List<Annotation> sorted = new ArrayList<>(List.of(annotations));
        sorted.sort(Comparator.comparing(annotation -> annotation.annotationType().getName()));
        return sorted;
    }

    private static boolean isRegistryAnnotation(Annotation annotation) {
        return annotation != null
                && REGISTRY_ANNOTATIONS.contains(annotation.annotationType().getSimpleName());
    }

    private static String methodSignature(Method method) {
        StringBuilder signature = new StringBuilder(method.getName()).append("(");
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (i > 0) {
                signature.append(",");
            }
            signature.append(parameterTypes[i].getName());
        }
        return signature.append(")").toString();
    }

    private static String sourceFor(Class<?> clazz, Map<String, String> classSources) {
        if (classSources != null) {
            String source = classSources.get(clazz.getName());
            if (source != null && !source.isBlank()) {
                return source;
            }
        }
        return "core";
    }

    private static String pluginId(String source) {
        if (source == null || !source.startsWith("plugin:")) {
            return null;
        }
        String remainder = source.substring("plugin:".length());
        int separator = remainder.indexOf(':');
        return separator < 0 ? remainder : remainder.substring(0, separator);
    }

    private static String normalizeSurfaceId(String surfaceId) {
        return surfaceId == null || surfaceId.isBlank() ? "default" : surfaceId.trim();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is not available", e);
        }
    }
}
