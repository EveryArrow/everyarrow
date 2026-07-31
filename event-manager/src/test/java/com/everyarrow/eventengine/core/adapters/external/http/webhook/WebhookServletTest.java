package com.everyarrow.eventengine.core.adapters.external.http.webhook;

import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.eventengine.core.services.registry.ServiceRegistry;
import com.everyarrow.eventengine.core.services.runtime.ServiceCall;
import com.everyarrow.eventengine.spi.handler.ServiceCallHandler;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.everyarrow.eventengine.spi.model.ServiceCallResponse;
import com.everyarrow.utilities.environment.Environment;
import com.google.gson.JsonObject;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookServletTest {

    @AfterEach
    void resetEnvironment() {
        Environment.reset();
        WebhookRequestVerifier.resetReplayCacheForTests();
    }

    @Test
    void protectedWebhookWithoutCredentialsReturnsUnauthorized() throws Exception {
        Environment.put("EA_WEBHOOK_SHARED_SECRET", "webhook-secret");
        Engine engine = new Engine();
        registerWebhook(engine.getRegistry(), "/webhooks/protected/demo", "tests.webhook.protected", false);
        engine.getRegistry().register("tests.webhook.protected", new EchoHandler(),
                engine.getRegistry().getDefinition("tests.webhook.protected"));

        WebhookServlet.setEngine(engine);
        WebhookServlet servlet = new WebhookServlet();
        CapturedResponse response = new CapturedResponse();
        String body = "{\"event\":\"demo\"}";

        servlet.service(
                fakeRequest("POST", "/webhooks/protected/demo", body, signedHeaders(body)),
                response.proxy());

        assertEquals(401, response.status());
        assertTrue(response.body().contains("Authentication failed"),
                "Expected protected webhook to fail closed without credentials");
    }

    @Test
    void webhookAuthorizationHeaderFlowsIntoEngineCall() throws Exception {
        Environment.put("EA_WEBHOOK_SHARED_SECRET", "webhook-secret");
        CapturingEngine engine = new CapturingEngine();
        registerWebhook(engine.getRegistry(), "/webhooks/internal/demo", "tests.webhook.internal", true);

        WebhookServlet.setEngine(engine);
        WebhookServlet servlet = new WebhookServlet();
        CapturedResponse response = new CapturedResponse();
        String body = "{\"event\":\"demo.created\"}";
        Map<String, String> headers = signedHeaders(body);
        headers.put("Authorization", "Bearer internal-service-token");

        servlet.service(
                fakeRequest(
                        "POST",
                        "/webhooks/internal/demo",
                        body,
                        headers),
                response.proxy());

        assertEquals(200, response.status());
        ServiceCall captured = engine.lastCall();
        assertNotNull(captured, "Engine should receive the webhook ServiceCall");
        assertEquals("tests.webhook.internal", captured.getType());
        assertEquals("Bearer internal-service-token", captured.getMetadata("http.authorization"));
        assertEquals("demo.created", captured.getParams().get("event").getAsString());
    }

    @Test
    void webhookWithoutSignatureFailsBeforeDispatch() throws Exception {
        Environment.put("EA_WEBHOOK_SHARED_SECRET", "webhook-secret");
        CapturingEngine engine = new CapturingEngine();
        registerWebhook(engine.getRegistry(), "/webhooks/internal/demo", "tests.webhook.internal", true);

        WebhookServlet.setEngine(engine);
        CapturedResponse response = new CapturedResponse();

        new WebhookServlet().service(
                fakeRequest("POST", "/webhooks/internal/demo", "{\"event\":\"demo\"}", Map.of()),
                response.proxy());

        assertEquals(401, response.status());
        assertTrue(response.body().contains("Webhook timestamp is required"));
        assertEquals(null, engine.lastCall());
    }

    @Test
    void webhookWithInvalidSignatureFailsBeforeDispatch() throws Exception {
        Environment.put("EA_WEBHOOK_SHARED_SECRET", "webhook-secret");
        CapturingEngine engine = new CapturingEngine();
        registerWebhook(engine.getRegistry(), "/webhooks/internal/demo", "tests.webhook.internal", true);

        String body = "{\"event\":\"demo\"}";
        Map<String, String> headers = signedHeaders(body);
        headers.put("X-EveryArrow-Webhook-Signature", "sha256=bad");

        WebhookServlet.setEngine(engine);
        CapturedResponse response = new CapturedResponse();

        new WebhookServlet().service(
                fakeRequest("POST", "/webhooks/internal/demo", body, headers),
                response.proxy());

        assertEquals(401, response.status());
        assertTrue(response.body().contains("Webhook signature is invalid"));
        assertEquals(null, engine.lastCall());
    }

    @Test
    void webhookWithStaleTimestampFailsBeforeDispatch() throws Exception {
        Environment.put("EA_WEBHOOK_SHARED_SECRET", "webhook-secret");
        CapturingEngine engine = new CapturingEngine();
        registerWebhook(engine.getRegistry(), "/webhooks/internal/demo", "tests.webhook.internal", true);

        String body = "{\"event\":\"demo\"}";
        String timestamp = String.valueOf(Instant.now().minusSeconds(600).getEpochSecond());
        Map<String, String> headers = signedHeaders(body, timestamp);

        WebhookServlet.setEngine(engine);
        CapturedResponse response = new CapturedResponse();

        new WebhookServlet().service(
                fakeRequest("POST", "/webhooks/internal/demo", body, headers),
                response.proxy());

        assertEquals(401, response.status());
        assertTrue(response.body().contains("Webhook timestamp is outside the allowed window"));
        assertEquals(null, engine.lastCall());
    }

    @Test
    void webhookReplayFailsBeforeSecondDispatch() throws Exception {
        Environment.put("EA_WEBHOOK_SHARED_SECRET", "webhook-secret");
        CapturingEngine engine = new CapturingEngine();
        registerWebhook(engine.getRegistry(), "/webhooks/internal/demo", "tests.webhook.internal", true);

        String body = "{\"event\":\"demo\"}";
        Map<String, String> headers = signedHeaders(body);

        WebhookServlet.setEngine(engine);
        WebhookServlet servlet = new WebhookServlet();
        CapturedResponse first = new CapturedResponse();
        servlet.service(fakeRequest("POST", "/webhooks/internal/demo", body, headers), first.proxy());

        CapturedResponse second = new CapturedResponse();
        servlet.service(fakeRequest("POST", "/webhooks/internal/demo", body, headers), second.proxy());

        assertEquals(200, first.status());
        assertEquals(401, second.status());
        assertTrue(second.body().contains("Webhook signature has already been used"));
    }

    @Test
    void spoofedAllowlistedOriginDoesNotBypassSignatureVerification() throws Exception {
        Environment.put("EA_WEBHOOK_UNSIGNED_ALLOWED_PATHS", "/webhooks/event");
        Environment.put("EA_WEBHOOK_UNSIGNED_ALLOWED_ORIGINS", "https://dev.everyarrow.io,https://www.everyarrow.io");
        Environment.put("EA_ALLOWED_CORS_ORIGINS", "https://dev.everyarrow.io,https://www.everyarrow.io");
        Environment.put("EA_WEBHOOK_SHARED_SECRET", "webhook-secret");

        CapturingEngine engine = new CapturingEngine();
        registerWebhook(engine.getRegistry(), "/webhooks/event", "tests.webhook.public_site", true);

        WebhookServlet.setEngine(engine);
        CapturedResponse response = new CapturedResponse();

        new WebhookServlet().service(
                fakeRequest(
                        "POST",
                        "/webhooks/event",
                        "{\"event\":\"demo\"}",
                        Map.of("Origin", "https://dev.everyarrow.io")),
                response.proxy());

        assertEquals(401, response.status());
        assertTrue(response.body().contains("Webhook timestamp is required"));
        assertEquals(null, engine.lastCall());
    }

    @Test
    void unsignedAllowlistedWebhookRejectsDisallowedOrigin() throws Exception {
        Environment.put("EA_WEBHOOK_UNSIGNED_ALLOWED_PATHS", "/webhooks/event");
        Environment.put("EA_WEBHOOK_UNSIGNED_ALLOWED_ORIGINS", "https://dev.everyarrow.io");
        Environment.put("EA_ALLOWED_CORS_ORIGINS", "https://dev.everyarrow.io");

        CapturingEngine engine = new CapturingEngine();
        registerWebhook(engine.getRegistry(), "/webhooks/event", "tests.webhook.public_site", true);

        WebhookServlet.setEngine(engine);
        CapturedResponse response = new CapturedResponse();

        new WebhookServlet().service(
                fakeRequest(
                        "POST",
                        "/webhooks/event",
                        "{\"event\":\"demo\"}",
                        Map.of("Origin", "https://evil.example.com")),
                response.proxy());

        assertEquals(403, response.status());
    }

    @Test
    void preflightReturnsOnlySupportedRequestedHeaders() throws Exception {
        Environment.put("EA_ALLOWED_CORS_ORIGINS", "https://console.everyarrow.io");
        CapturedResponse response = new CapturedResponse();

        new WebhookServlet().service(
                fakeRequest(
                        "OPTIONS",
                        "/webhooks/event",
                        "",
                        Map.of(
                                "Origin", "https://console.everyarrow.io",
                                "Access-Control-Request-Headers",
                                "X-EveryArrow-Authorization, X-Not-Allowed, Content-Type")),
                response.proxy());

        assertEquals(204, response.status());
        assertEquals(
                "X-EveryArrow-Authorization, Content-Type",
                response.header("Access-Control-Allow-Headers"));
    }

    @Test
    void oversizedWebhookBodyFailsBeforeVerificationOrDispatch() throws Exception {
        Environment.put("EA_HTTP_MAX_REQUEST_BODY_BYTES", "8");
        Environment.put("EA_WEBHOOK_SHARED_SECRET", "webhook-secret");

        CapturingEngine engine = new CapturingEngine();
        registerWebhook(engine.getRegistry(), "/webhooks/internal/demo", "tests.webhook.internal", true);

        WebhookServlet.setEngine(engine);
        CapturedResponse response = new CapturedResponse();

        new WebhookServlet().service(
                fakeRequest("POST", "/webhooks/internal/demo", "{\"event\":\"too-large\"}", Map.of()),
                response.proxy());

        assertEquals(413, response.status());
        assertTrue(response.body().contains("Request body too large"));
        assertEquals(null, engine.lastCall());
    }

    private static void registerWebhook(ServiceRegistry registry, String path, String serviceType, boolean isPublic) {
        ServiceCallDefinition definition = new ServiceCallDefinition();
        definition.setServiceType(serviceType);
        definition.setHandlerClass(EchoHandler.class.getName());
        definition.setCategory("Test");
        definition.setSource("test");

        ServiceCallDefinition.Annotations annotations = new ServiceCallDefinition.Annotations();
        ServiceCallDefinition.WebhookMeta webhook = new ServiceCallDefinition.WebhookMeta();
        webhook.path = path;
        annotations.webhook = webhook;

        if (isPublic) {
            ServiceCallDefinition.SecuredMeta secured = new ServiceCallDefinition.SecuredMeta();
            secured.isPublic = true;
            annotations.secured = secured;
        }

        definition.setAnnotations(annotations);
        registry.registerDefinition(definition);
    }

    private static HttpServletRequest fakeRequest(String method,
                                                  String uri,
                                                  String body,
                                                  Map<String, String> headers) {
        Map<String, String> safeHeaders = headers == null ? Map.of() : headers;

        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class[]{HttpServletRequest.class},
                (proxy, invokedMethod, args) -> switch (invokedMethod.getName()) {
                    case "getMethod" -> method;
                    case "getRequestURI" -> uri;
                    case "getHeader" -> safeHeaders.get(args[0]);
                    case "getReader" -> new BufferedReader(new StringReader(body == null ? "" : body));
                    case "getInputStream" -> inputStream(body == null ? "" : body);
                    case "toString" -> "FakeWebhookRequest";
                    default -> defaultValue(invokedMethod.getReturnType());
                });
    }

    private static Map<String, String> signedHeaders(String body) {
        return signedHeaders(body, String.valueOf(Instant.now().getEpochSecond()));
    }

    private static Map<String, String> signedHeaders(String body, String timestamp) {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-EveryArrow-Webhook-Timestamp", timestamp);
        headers.put("X-EveryArrow-Webhook-Signature",
                "sha256=" + WebhookRequestVerifier.hmacSha256("webhook-secret", timestamp + "." + body));
        return headers;
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

    private static final class CapturedResponse {
        private final AtomicInteger status = new AtomicInteger(200);
        private final StringWriter body = new StringWriter();
        private final AtomicReference<String> contentType = new AtomicReference<>();
        private final Map<String, String> headers = new HashMap<>();

        HttpServletResponse proxy() {
            return (HttpServletResponse) Proxy.newProxyInstance(
                    HttpServletResponse.class.getClassLoader(),
                    new Class[]{HttpServletResponse.class},
                    (proxy, invokedMethod, args) -> switch (invokedMethod.getName()) {
                        case "setStatus" -> {
                            status.set((Integer) args[0]);
                            yield null;
                        }
                        case "setContentType" -> {
                            contentType.set((String) args[0]);
                            yield null;
                        }
                        case "setHeader" -> {
                            headers.put((String) args[0], (String) args[1]);
                            yield null;
                        }
                        case "getWriter" -> new PrintWriter(body, true);
                        case "toString" -> "CapturedWebhookResponse";
                        default -> defaultValue(invokedMethod.getReturnType());
                    });
        }

        int status() {
            return status.get();
        }

        String body() {
            return body.toString();
        }

        String contentType() {
            return contentType.get();
        }

        String header(String name) {
            return headers.get(name);
        }
    }

    private static final class EchoHandler implements ServiceCallHandler {
        @Override
        public JsonObject execute(JsonObject params, String type, String source) {
            JsonObject out = new JsonObject();
            out.addProperty("status", "ok");
            out.addProperty("type", type);
            return out;
        }

        @Override
        public JsonObject onError(ServiceCall call, Exception ex) {
            JsonObject error = new JsonObject();
            error.addProperty("error", ex == null ? "unknown" : ex.getMessage());
            return error;
        }
    }

    private static final class CapturingEngine extends Engine {
        private final AtomicReference<ServiceCall> lastCall = new AtomicReference<>();

        @Override
        public ServiceCallResponse execute(ServiceCall call) {
            lastCall.set(call);
            JsonObject out = new JsonObject();
            out.addProperty("status", "ok");
            return ServiceCallResponse.ok(call.getType(), out, 0L);
        }

        ServiceCall lastCall() {
            return lastCall.get();
        }
    }
}
