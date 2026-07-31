package com.everyarrow.eventengine.tests.core;

import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.eventengine.core.adapters.AnnotationDefinitionBuilder;
import com.everyarrow.eventengine.core.services.runtime.ServiceCall;
import com.everyarrow.eventengine.core.services.runtime.ServiceCallSource;
import com.everyarrow.eventengine.spi.annotations.ArtemisQueueSubscription;
import com.everyarrow.eventengine.spi.annotations.CalendarJob;
import com.everyarrow.eventengine.spi.annotations.FunctionContract;
import com.everyarrow.eventengine.spi.annotations.FunctionOverride;
import com.everyarrow.eventengine.spi.annotations.PostgresNotification;
import com.everyarrow.eventengine.spi.annotations.RestRoute;
import com.everyarrow.eventengine.spi.annotations.ServiceCallName;
import com.everyarrow.eventengine.spi.handler.ServiceCallHandler;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.everyarrow.eventengine.spi.model.ServiceCallResponse;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CallableAnnotationMetadataTest {

    @Test
    void extractsSharedCallableMetadataFromServiceCallAndTransportAnnotations() {
        List<ServiceCallDefinition> definitions = AnnotationDefinitionBuilder.buildDefinitions(MetadataHandler.class);

        ServiceCallDefinition rest = find(definitions, "metadata.rest");
        assertNotNull(rest.getAnnotations().call);
        assertEquals("MARKETING", rest.getAnnotations().call.applicationKey);
        assertNotNull(rest.getAnnotations().perch);
        assertEquals("Marketing", rest.getAnnotations().perch.applicationName);
        assertEquals("LinkedIn", rest.getAnnotations().perch.systemName);
        assertEquals("Rest metadata", rest.getAnnotations().perch.description);

        ServiceCallDefinition queue = find(definitions, "metadata.queue");
        assertTrue(queue.hasQueueSubscriptions());
        assertEquals("QUEUE_APP", queue.getAnnotations().call.applicationKey);
        assertEquals("QueueSystem", queue.getAnnotations().perch.systemName);

        ServiceCallDefinition calendar = find(definitions, "metadata.calendar");
        assertTrue(calendar.getAnnotations().call.open);
        assertTrue(calendar.hasCalendarJobs());
        assertTrue(calendar.getCalendarJobs().getFirst().startup);

        ServiceCallDefinition notification = find(definitions, "metadata.postgres-notification");
        assertTrue(notification.hasPostgresNotifications());
        assertTrue(notification.getAnnotations().call.open);
        assertEquals("EveryArrow", notification.getPostgresNotifications().getFirst().database);
        assertEquals("app_security_changed", notification.getPostgresNotifications().getFirst().channel);
    }

    @Test
    void serviceCallNameOnlyMethodStillCreatesServiceDefinition() {
        List<ServiceCallDefinition> definitions = AnnotationDefinitionBuilder.buildDefinitions(NamedOnlyHandler.class);

        ServiceCallDefinition definition = find(definitions, "metadata.named-only");
        assertEquals("namedOnly", definition.getTargetMethodName());
        assertEquals(NamedOnlyHandler.class.getName(), definition.getClassName());
    }

    @Test
    void extractsAndSerializesExplicitFunctionOverrideContract() {
        ServiceCallDefinition definition = find(
                AnnotationDefinitionBuilder.buildDefinitions(
                        OverrideHandler.class),
                "security.access_control.api_key_create");

        assertEquals("security.access_control",
                definition.getAnnotations().functionContract.namespace);
        assertEquals("1.2.0",
                definition.getAnnotations().functionContract.version);
        assertEquals("api-key-create-v1",
                definition.getAnnotations().functionContract.signature);
        assertEquals(List.of("service_call", "jsonb", "audit"),
                definition.getAnnotations().functionContract.capabilities);
        assertEquals("generated-security",
                definition.getAnnotations().functionOverride.targetPluginId);
        assertEquals("[1.0.0,2.0.0)",
                definition.getAnnotations().functionOverride.versionRange);

        ServiceCallDefinition restored = ServiceCallDefinition.fromJson(
                ServiceCallDefinition.toJson(definition));
        assertEquals("api-key-create-v1",
                restored.getAnnotations().functionContract.signature);
        assertEquals("generated-security",
                restored.getAnnotations().functionOverride.targetPluginId);
    }

    @Test
    void dispatchPrefersServiceCallTargetMethodAndPreservesMetadata() throws Exception {
        Engine engine = new Engine();
        ServiceCallDefinition definition = new ServiceCallDefinition();
        definition.setServiceType("metadata.dispatch");
        definition.setTargetMethodName("handle");
        definition.setActive(true);
        engine.getRegistry().register("metadata.dispatch", new DispatchHandler(), definition);

        ServiceCall call = new ServiceCall();
        call.setType("metadata.dispatch");
        call.setSource(ServiceCallSource.INTERNAL);
        call.putMetadata("perch.jobExecutionInstanceId", "job-exec-1");

        JsonObject body = engine.execute(call).getBody().getAsJsonObject();

        assertEquals("metadata.dispatch", body.get("type").getAsString());
        assertEquals("job-exec-1", body.get("jobExecutionInstanceId").getAsString());
    }

    @Test
    void dispatchFailsClosedWhenTargetMethodIsMissing() throws Exception {
        Engine engine = new Engine();
        ServiceCallDefinition definition = new ServiceCallDefinition();
        definition.setServiceType("metadata.missing-target");
        definition.setTargetMethodName("missing");
        definition.setActive(true);
        engine.getRegistry().register("metadata.missing-target", new DispatchHandler(), definition);

        ServiceCall call = new ServiceCall();
        call.setType("metadata.missing-target");
        call.setSource(ServiceCallSource.INTERNAL);

        ServiceCallResponse response = engine.execute(call);

        assertEquals(500, response.getHttpStatus());
        assertEquals("Internal server error",
                response.getBody().getAsJsonObject().get("error").getAsString());
    }

    private static ServiceCallDefinition find(List<ServiceCallDefinition> definitions, String serviceType) {
        return definitions.stream()
                .filter(definition -> serviceType.equals(definition.getServiceType()))
                .findFirst()
                .orElseThrow();
    }

    private static final class MetadataHandler implements ServiceCallHandler {

        @ServiceCallName(
                value = "metadata.rest",
                applicationKey = "MARKETING",
                perchApplicationName = "Marketing",
                perchSystemName = "LinkedIn",
                perchDescription = "Rest metadata")
        @RestRoute(path = "/metadata/rest", method = "POST")
        public JsonElement rest(ServiceCall call) {
            return new JsonObject();
        }

        @ServiceCallName("metadata.queue")
        @ArtemisQueueSubscription(
                destination = "metadata.queue",
                applicationKey = "QUEUE_APP",
                perchApplicationName = "QueueApp",
                perchSystemName = "QueueSystem")
        public JsonElement queue(ServiceCall call) {
            return new JsonObject();
        }

        @ServiceCallName("metadata.calendar")
        @CalendarJob(cron = "0 0 1 * * ?", startup = true, open = true)
        public JsonElement calendar(ServiceCall call) {
            return new JsonObject();
        }

        @ServiceCallName("metadata.postgres-notification")
        @PostgresNotification(database = "EveryArrow", channel = "app_security_changed", open = true)
        public JsonElement postgresNotification(ServiceCall call) {
            return new JsonObject();
        }

        @Override
        public JsonElement execute(JsonObject params, String type, String source) {
            return new JsonObject();
        }
    }

    private static final class DispatchHandler implements ServiceCallHandler {

        public JsonElement handle(ServiceCall call) {
            JsonObject result = new JsonObject();
            result.addProperty("type", call.getType());
            result.addProperty("jobExecutionInstanceId",
                    call.getMetadata("perch.jobExecutionInstanceId").toString());
            return result;
        }

        @Override
        public JsonElement execute(JsonObject params, String type, String source) {
            JsonObject result = new JsonObject();
            result.addProperty("fallback", true);
            return result;
        }
    }

    private static final class OverrideHandler
            implements ServiceCallHandler {

        @ServiceCallName("security.access_control.api_key_create")
        @FunctionContract(
                namespace = "security.access_control",
                version = "1.2.0",
                signature = "api-key-create-v1",
                capabilities = {"service_call", "jsonb", "audit"})
        @FunctionOverride(
                serviceType = "security.access_control.api_key_create",
                pluginId = "generated-security",
                versionRange = "[1.0.0,2.0.0)")
        public JsonElement create(ServiceCall call) {
            return new JsonObject();
        }

        @Override
        public JsonElement execute(
                JsonObject params,
                String type,
                String source) {
            return new JsonObject();
        }
    }

    private static final class NamedOnlyHandler implements ServiceCallHandler {

        @ServiceCallName("metadata.named-only")
        public JsonElement namedOnly(ServiceCall call) {
            return new JsonObject();
        }

        @Override
        public JsonElement execute(JsonObject params, String type, String source) {
            return new JsonObject();
        }
    }
}
