/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.registry;

import com.everyarrow.eventengine.core.services.runtime.GenericProcessHandler;
import com.everyarrow.eventengine.spi.annotations.CalendarJob;
import com.everyarrow.eventengine.spi.annotations.RestRoute;
import com.everyarrow.eventengine.spi.annotations.Secured;
import com.everyarrow.eventengine.spi.annotations.ServiceCallName;
import com.everyarrow.eventengine.spi.handler.ServiceCallHandler;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DbServiceRegistryProjectionTest {

    @Test
    void javaHandlerProjectionKeepsHandlerAndSurfaceMetadata() {
        ServiceCallDefinition definition = new ServiceCallDefinition();
        definition.setServiceType("sample.tools.run");
        definition.setHandlerClass("com.everyarrow.plugins.sample.SampleToolsProcessor");
        definition.setClassName("com.everyarrow.plugins.sample.SampleToolsProcessor");
        definition.setTargetMethodName("runTool");
        definition.setCategory("Tools");
        definition.setSource("plugin:sample-tools");
        definition.setRestRoute(restRoute("POST", "/sample/tools/run"));
        definition.annotations.call = new ServiceCallDefinition.CallMeta();
        definition.annotations.call.applicationKey = "SAMPLE";

        DbServiceRegistryProjection projection = DbServiceRegistryProjection
                .from(definition, "everyarrow-combined")
                .orElseThrow();

        assertEquals(DbServiceRegistryProjection.KIND_JAVA_HANDLER, projection.registryEntryKind());
        assertEquals("event_manager", projection.databaseName());
        assertEquals("java_handler", projection.tableName());
        assertEquals("com.everyarrow.plugins.sample.SampleToolsProcessor", projection.handlerClass());
        assertEquals("runTool", projection.targetMethodName());
        assertEquals("sample-tools", projection.pluginId());
        assertEquals("everyarrow-combined", projection.surfaceId());
        assertEquals("POST", projection.httpMethod());
        assertEquals("/sample/tools/run", projection.routePath());
        assertNotNull(projection.metadataHash());
    }

    @Test
    void dbFunctionProjectionDerivesFunctionLocationFromRegistryMetadata() {
        ServiceCallDefinition definition = new ServiceCallDefinition();
        definition.setServiceType("marketing.contact.crm_dashboard_stats");
        definition.setHandlerClass(GenericProcessHandler.class.getSimpleName());
        definition.setSource("db:marketing");
        definition.setJsonType("jsonb");
        definition.setRestRoute(restRoute("GET", "/marketing/crm/dashboard"));
        definition.annotations.call = new ServiceCallDefinition.CallMeta();
        definition.annotations.call.applicationKey = "MARKETING";

        DbServiceRegistryProjection projection = DbServiceRegistryProjection
                .from(definition, "everyarrow-marketing")
                .orElseThrow();

        assertEquals(DbServiceRegistryProjection.KIND_DB_FUNCTION, projection.registryEntryKind());
        assertEquals("marketing", projection.databaseName());
        assertEquals("contact", projection.schemaName());
        assertEquals("crm_dashboard_stats", projection.functionName());
        assertEquals("db_function", projection.functionType());
        assertEquals("jsonb", projection.jsonType());
        assertEquals("GET", projection.httpMethod());
        assertEquals("/marketing/crm/dashboard", projection.routePath());
    }

    @Test
    void indexOnlyHandlerWithoutAnnotationsIsNotPersisted() {
        ServiceCallDefinition definition = new ServiceCallDefinition();
        definition.setServiceType("sample.indexOnly");
        definition.setHandlerClass("com.everyarrow.plugins.sample.IndexOnlyHandler");
        definition.setSource("plugin:sample");

        Optional<DbServiceRegistryProjection> projection =
                DbServiceRegistryProjection.from(definition, "everyarrow-combined");

        assertFalse(projection.isPresent());
    }

    @Test
    void annotationMetadataProjectionKeepsOneRowPerAnnotationOccurrence() {
        List<DbServiceRegistryProjection> projections = DbAnnotationMetadataProjection.fromClasses(
                List.of(AnnotatedProjectionHandler.class),
                Map.of(AnnotatedProjectionHandler.class.getName(), "plugin:sample-tools"),
                "everyarrow-combined");

        assertEquals(4, projections.size());
        assertEquals(1, projections.stream()
                .filter(projection -> DbServiceRegistryProjection.KIND_JAVA_HANDLER
                        .equals(projection.registryEntryKind()))
                .count());
        assertEquals(3, projections.stream()
                .filter(projection -> DbServiceRegistryProjection.KIND_ANNOTATION_METADATA
                        .equals(projection.registryEntryKind()))
                .count());

        DbServiceRegistryProjection serviceCallProjection = projections.stream()
                .filter(projection -> "sample.projection.run".equals(projection.serviceType()))
                .findFirst()
                .orElseThrow();

        assertEquals(DbServiceRegistryProjection.KIND_JAVA_HANDLER,
                serviceCallProjection.registryEntryKind());
        assertEquals("plugin:sample-tools", serviceCallProjection.source());
        assertEquals("sample-tools", serviceCallProjection.pluginId());
        assertEquals("everyarrow-combined", serviceCallProjection.surfaceId());
        assertEquals("run", serviceCallProjection.targetMethodName());
        assertEquals("POST", serviceCallProjection.httpMethod());
        assertEquals("/sample/projection/run", serviceCallProjection.routePath());
        assertNotNull(serviceCallProjection.metadataHash());
    }

    @Test
    void classLevelServiceCallProjectsAsExecutableHandler() {
        List<DbServiceRegistryProjection> projections = DbAnnotationMetadataProjection.fromClasses(
                List.of(ClassLevelProjectionHandler.class),
                Map.of(ClassLevelProjectionHandler.class.getName(), "core"),
                "everyarrow-combined");

        assertEquals(1, projections.size());
        DbServiceRegistryProjection projection = projections.getFirst();
        assertEquals("sample.projection.class-level", projection.serviceType());
        assertEquals(DbServiceRegistryProjection.KIND_JAVA_HANDLER, projection.registryEntryKind());
        assertEquals(ClassLevelProjectionHandler.class.getName(), projection.handlerClass());
    }

    @Test
    void emptyDiscoveryStillRefreshesSurfaceSoDatabaseCanDeactivateStaleRows() throws Exception {
        AtomicReference<String> payload = new AtomicReference<>();
        AtomicBoolean firstRow = new AtomicBoolean(true);

        ResultSet resultSet = proxy(ResultSet.class, (method, args) -> switch (method) {
            case "next" -> firstRow.getAndSet(false);
            case "getString" -> "{\"refreshed_count\":0}";
            default -> null;
        });
        PreparedStatement statement = proxy(PreparedStatement.class, (method, args) -> switch (method) {
            case "setString" -> {
                payload.set((String) args[1]);
                yield null;
            }
            case "executeQuery" -> resultSet;
            default -> null;
        });
        Connection connection = proxy(Connection.class, (method, args) ->
                "prepareStatement".equals(method) ? statement : null);

        int refreshed = new DbServiceRegistryRefresher().refresh(
                connection,
                List.of(),
                List.of(),
                Map.of(),
                "server-a");

        JsonObject refreshPayload = com.google.gson.JsonParser.parseString(payload.get()).getAsJsonObject();
        assertEquals(0, refreshed);
        assertEquals("server-a", refreshPayload.get("surface_id").getAsString());
        assertTrue(refreshPayload.getAsJsonArray("entries").isEmpty());
    }

    private <T> T proxy(Class<T> type, ProxyInvocation invocation) {
        Object instance = Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (ignored, method, args) -> invocation.invoke(method.getName(), args));
        return type.cast(instance);
    }

    @FunctionalInterface
    private interface ProxyInvocation {
        Object invoke(String method, Object[] args) throws Throwable;
    }

    private ServiceCallDefinition.RestRouteMeta restRoute(String method, String path) {
        ServiceCallDefinition.RestRouteMeta route = new ServiceCallDefinition.RestRouteMeta();
        route.method = method;
        route.path = path;
        return route;
    }

    private static final class AnnotatedProjectionHandler implements ServiceCallHandler {

        @ServiceCallName("sample.projection.run")
        @RestRoute(method = "POST", path = "/sample/projection/run")
        @Secured(roles = "admin")
        JsonElement run(JsonObject params, String type, String source) {
            return new JsonObject();
        }

        @CalendarJob(id = "sample-projection-hourly", cron = "0 0 * * * ?")
        JsonElement scheduled(JsonObject params, String type, String source) {
            return new JsonObject();
        }

        @Override
        public JsonElement execute(JsonObject params, String type, String source) {
            return new JsonObject();
        }
    }

    @ServiceCallName("sample.projection.class-level")
    private static final class ClassLevelProjectionHandler implements ServiceCallHandler {

        @Override
        public JsonElement execute(JsonObject params, String type, String source) {
            return new JsonObject();
        }
    }
}
