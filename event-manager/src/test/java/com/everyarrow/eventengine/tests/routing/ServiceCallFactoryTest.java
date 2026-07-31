package com.everyarrow.eventengine.tests.routing;

import com.everyarrow.eventengine.core.routing.ServiceCallFactory;
import com.everyarrow.eventengine.core.services.runtime.PerchRuntimeMetadata;
import com.everyarrow.eventengine.core.services.runtime.SecurityContext;
import com.everyarrow.eventengine.core.services.runtime.ServiceCall;
import com.everyarrow.eventengine.core.services.runtime.ServiceCallSource;
import com.google.gson.JsonObject;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceCallFactoryTest {

    @Test
    void restFactoryCopiesAuthMetadata() throws Exception {
        HttpServletRequest request = fakeRequest(
                "POST",
                "/api/tests/echo",
                "mode=test",
                "{\"message\":\"hello\"}",
                Map.of(
                        "Authorization", "Bearer token-123",
                        "x-api-key", "api-key-xyz",
                        "X-EveryArrow-Customer-Id", "customer-123"
                ));

        ServiceCall call = ServiceCallFactory.fromRest(request, "tests.rest.echo");

        assertEquals(ServiceCallSource.REST, call.getSource());
        assertEquals("/api/tests/echo", call.getMetadata("http.path"));
        assertEquals("Bearer token-123", call.getMetadata("http.authorization"));
        assertEquals("api-key-xyz", call.getMetadata("http.x-api-key"));
        assertEquals("customer-123", call.getMetadata("http.customer-id"));
        assertEquals("hello", call.getParams().get("message").getAsString());
        assertEquals("test", call.getParams().get("mode").getAsString());
    }

    @Test
    void restFactoryNormalizesPerchPayloadContext() throws Exception {
        HttpServletRequest request = fakeRequest(
                "POST",
                "/api/tests/processor",
                null,
                """
                        {
                          "message": "hello",
                          "perch": {
                            "jobGroupExecutionInstanceId": "group-exec-1",
                            "jobExecutionInstanceId": "job-exec-1",
                            "taskExecutionInstanceId": "task-exec-1"
                          }
                        }
                        """,
                Map.of());

        ServiceCall call = ServiceCallFactory.fromRest(request, "tests.processor");

        assertEquals("group-exec-1", call.getMetadata(PerchRuntimeMetadata.JOB_GROUP_EXECUTION_INSTANCE_ID));
        assertEquals("job-exec-1", call.getMetadata(PerchRuntimeMetadata.JOB_EXECUTION_INSTANCE_ID));
        assertEquals("task-exec-1", call.getMetadata(PerchRuntimeMetadata.TASK_EXECUTION_INSTANCE_ID));
    }

    @Test
    void restFactoryNormalizesPerchHeaderContext() throws Exception {
        HttpServletRequest request = fakeRequest(
                "POST",
                "/api/tests/processor",
                null,
                "{\"message\":\"hello\"}",
                Map.of(
                        "ea_perch_job_group_execution_instance_id", "group-exec-header",
                        "ea_perch_job_execution_instance_id", "job-exec-header",
                        "ea_perch_task_execution_instance_id", "task-exec-header"
                ));

        ServiceCall call = ServiceCallFactory.fromRest(request, "tests.processor");

        assertEquals("group-exec-header", call.getMetadata(PerchRuntimeMetadata.JOB_GROUP_EXECUTION_INSTANCE_ID));
        assertEquals("job-exec-header", call.getMetadata(PerchRuntimeMetadata.JOB_EXECUTION_INSTANCE_ID));
        assertEquals("task-exec-header", call.getMetadata(PerchRuntimeMetadata.TASK_EXECUTION_INSTANCE_ID));
    }

    @Test
    void webhookFactoryCopiesAuthMetadata() throws Exception {
        HttpServletRequest request = fakeRequest(
                "POST",
                "/webhooks/internal/demo",
                null,
                "{\"event\":\"demo.created\"}",
                Map.of(
                        "Authorization", "Bearer service-token",
                        "x-api-key", "webhook-api-key"
                ));

        ServiceCall call = ServiceCallFactory.fromWebhook(request, "tests.webhook.demo");

        assertEquals(ServiceCallSource.WEBHOOK, call.getSource());
        assertEquals("/webhooks/internal/demo", call.getMetadata("http.path"));
        assertEquals("Bearer service-token", call.getMetadata("http.authorization"));
        assertEquals("webhook-api-key", call.getMetadata("http.x-api-key"));
        assertEquals("demo.created", call.getParams().get("event").getAsString());
    }

    @Test
    void restFactoryRejectsMalformedJsonBody() {
        HttpServletRequest request = fakeRequest(
                "POST",
                "/api/tests/echo",
                null,
                "{\"message\":",
                Map.of());

        assertThrows(ServiceCallFactory.InvalidJsonBodyException.class,
                () -> ServiceCallFactory.fromRest(request, "tests.rest.echo"));
    }

    @Test
    void webhookFactoryRejectsMalformedJsonBody() {
        HttpServletRequest request = fakeRequest(
                "POST",
                "/webhooks/internal/demo",
                null,
                "{\"event\":",
                Map.of());

        assertThrows(ServiceCallFactory.InvalidJsonBodyException.class,
                () -> ServiceCallFactory.fromWebhook(request, "tests.webhook.demo"));
    }

    @Test
    void webhookFactoryNormalizesPerchHeaderContext() throws Exception {
        HttpServletRequest request = fakeRequest(
                "POST",
                "/webhooks/internal/demo",
                null,
                "{\"event\":\"demo.created\"}",
                Map.of(
                        "ea_perch_job_group_execution_instance_id", "group-exec-webhook",
                        "ea_perch_job_execution_instance_id", "job-exec-webhook",
                        "ea_perch_task_execution_instance_id", "task-exec-webhook"
                ));

        ServiceCall call = ServiceCallFactory.fromWebhook(request, "tests.webhook.demo");

        assertEquals("group-exec-webhook", call.getMetadata(PerchRuntimeMetadata.JOB_GROUP_EXECUTION_INSTANCE_ID));
        assertEquals("job-exec-webhook", call.getMetadata(PerchRuntimeMetadata.JOB_EXECUTION_INSTANCE_ID));
        assertEquals("task-exec-webhook", call.getMetadata(PerchRuntimeMetadata.TASK_EXECUTION_INSTANCE_ID));
    }

    @Test
    void websocketFactoryCopiesConnectionAuthMetadata() {
        JsonObject payload = new JsonObject();
        payload.addProperty("correlationId", "corr-123");

        ServiceCall call = ServiceCallFactory.fromWebSocket(
                payload,
                "tests.websocket.echo",
                "/ws/tests/echo",
                Map.of(
                        "http.authorization", "Bearer token-123",
                        "http.x-api-key", "api-key-xyz",
                        "http.customer-id", "customer-123"
                ));

        assertEquals(ServiceCallSource.WEBSOCKET, call.getSource());
        assertEquals("/ws/tests/echo", call.getMetadata("websocket.path"));
        assertEquals("corr-123", call.getMetadata("websocket.correlationId"));
        assertEquals("Bearer token-123", call.getMetadata("http.authorization"));
        assertEquals("api-key-xyz", call.getMetadata("http.x-api-key"));
        assertEquals("customer-123", call.getMetadata("http.customer-id"));
    }

    @Test
    void websocketFactoryStillUsesPayloadParamsContract() {
        JsonObject params = new JsonObject();
        params.addProperty("message", "hello");

        JsonObject payload = new JsonObject();
        payload.add("params", params);

        ServiceCall call = ServiceCallFactory.fromWebSocket(payload, "tests.websocket.echo", "/ws/tests/echo");

        assertEquals("hello", call.getParams().get("message").getAsString());
        assertNull(call.getSecurityContext());
    }

    @Test
    void websocketFactoryNormalizesTopLevelPerchPayloadContext() {
        JsonObject perch = new JsonObject();
        perch.addProperty("jobExecutionInstanceId", "job-exec-ws");

        JsonObject params = new JsonObject();
        params.addProperty("message", "hello");

        JsonObject payload = new JsonObject();
        payload.add("params", params);
        payload.add("perch", perch);

        ServiceCall call = ServiceCallFactory.fromWebSocket(payload, "tests.websocket.echo", "/ws/tests/echo");

        assertEquals("job-exec-ws", call.getMetadata(PerchRuntimeMetadata.JOB_EXECUTION_INSTANCE_ID));
        assertEquals("hello", call.getParams().get("message").getAsString());
    }

    private static HttpServletRequest fakeRequest(String method,
                                                  String uri,
                                                  String query,
                                                  String body,
                                                  Map<String, String> headers) {
        Map<String, String> safeHeaders = headers == null ? Map.of() : headers;

        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class[]{HttpServletRequest.class},
                (proxy, invokedMethod, args) -> switch (invokedMethod.getName()) {
                    case "getMethod" -> method;
                    case "getRequestURI" -> uri;
                    case "getQueryString" -> query;
                    case "getHeader" -> safeHeaders.get(args[0]);
                    case "getInputStream" -> inputStream(body == null ? "" : body);
                    case "toString" -> "FakeHttpServletRequest";
                    default -> defaultValue(invokedMethod.getReturnType());
                });
    }

    private static ServletInputStream inputStream(String body) {
        ByteArrayInputStream delegate = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        return new ServletInputStream() {
            @Override
            public int read() {
                return delegate.read();
            }

            @Override
            public boolean isFinished() {
                return delegate.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
            }
        };
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == char.class) {
            return '\0';
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0f;
        }
        if (returnType == double.class) {
            return 0d;
        }
        return null;
    }
}
