package com.everyarrow.eventengine.core.adapters.external.http.webhook;

import com.google.gson.JsonObject;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookHandlerAdapterTest {

    @Test
    void processesValidWebhook() throws Exception {
        RecordingIdentifier identifier = new RecordingIdentifier();
        TestAdapter adapter = new TestAdapter(identifier);
        CapturedResponse response = new CapturedResponse();

        adapter.doPost(request("/vendor", "{\"type\":\"created\"}"), response.proxy());

        assertEquals(HttpServletResponse.SC_OK, response.status.get());
        assertTrue(identifier.processed);
    }

    @Test
    void rejectsInvalidSignatureWithoutProcessing() throws Exception {
        RecordingIdentifier identifier = new RecordingIdentifier();
        identifier.signatureFailure = new SecurityException("sensitive verifier detail");
        TestAdapter adapter = new TestAdapter(identifier);
        CapturedResponse response = new CapturedResponse();

        adapter.doPost(request("/vendor", "{}"), response.proxy());

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.status.get());
        assertEquals("Webhook signature verification failed.", response.message.get());
        assertFalse(identifier.processed);
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        RecordingIdentifier identifier = new RecordingIdentifier();
        TestAdapter adapter = new TestAdapter(identifier);
        CapturedResponse response = new CapturedResponse();

        adapter.doPost(request("/vendor", "not-json"), response.proxy());

        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.status.get());
        assertEquals("Webhook request body must be a JSON object.", response.message.get());
        assertFalse(identifier.processed);
    }

    @Test
    void processingFailureDoesNotLeakExceptionDetails() throws Exception {
        RecordingIdentifier identifier = new RecordingIdentifier();
        identifier.processingFailure = new IllegalArgumentException("database password leaked");
        TestAdapter adapter = new TestAdapter(identifier);
        CapturedResponse response = new CapturedResponse();

        adapter.doPost(request("/vendor", "{}"), response.proxy());

        assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, response.status.get());
        assertEquals("Webhook processing failed.", response.message.get());
        assertFalse(response.message.get().contains("database password"));
    }

    @Test
    void unsupportedVendorIsRejectedBeforeReadingBody() throws Exception {
        TestAdapter adapter = new TestAdapter(new RecordingIdentifier());
        CapturedResponse response = new CapturedResponse();

        adapter.doPost(request("/unknown", null), response.proxy());

        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.status.get());
        assertTrue(response.message.get().contains("Unsupported webhook vendor"));
    }

    private static HttpServletRequest request(String pathInfo, String body) {
        byte[] bytes = body == null ? null : body.getBytes(StandardCharsets.UTF_8);
        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class[]{HttpServletRequest.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getPathInfo" -> pathInfo;
                    case "getContentLengthLong" -> bytes == null ? -1L : (long) bytes.length;
                    case "getInputStream" -> {
                        if (bytes == null) {
                            throw new AssertionError("Request body should not be read");
                        }
                        yield servletInputStream(bytes);
                    }
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static ServletInputStream servletInputStream(byte[] body) {
        ByteArrayInputStream input = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return input.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int read() {
                return input.read();
            }

            @Override
            public int read(byte[] bytes, int offset, int length) {
                return input.read(bytes, offset, length);
            }
        };
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class || type == short.class || type == byte.class || type == char.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        return null;
    }

    private static final class TestAdapter extends WebhookHandlerAdapter {
        private final WebhookIdentifier identifier;

        private TestAdapter(WebhookIdentifier identifier) {
            this.identifier = identifier;
            initializeIdentifiers();
        }

        @Override
        protected void initializeIdentifiers() {
            if (identifier != null) {
                registerIdentifier("vendor", identifier);
            }
        }
    }

    private static final class RecordingIdentifier implements WebhookIdentifier {
        private SecurityException signatureFailure;
        private RuntimeException processingFailure;
        private boolean processed;

        @Override
        public void verifySignature(String requestBody, HttpServletRequest request) {
            if (signatureFailure != null) {
                throw signatureFailure;
            }
        }

        @Override
        public String getEventType(JsonObject requestBody, HttpServletRequest request) {
            return requestBody.has("type") ? requestBody.get("type").getAsString() : "test";
        }

        @Override
        public void process(String eventType, JsonObject payload) {
            if (processingFailure != null) {
                throw processingFailure;
            }
            processed = true;
        }
    }

    private static final class CapturedResponse {
        private final AtomicInteger status = new AtomicInteger();
        private final AtomicReference<String> message = new AtomicReference<>();

        private HttpServletResponse proxy() {
            return (HttpServletResponse) Proxy.newProxyInstance(
                    HttpServletResponse.class.getClassLoader(),
                    new Class[]{HttpServletResponse.class},
                    (proxy, method, args) -> {
                        if ("setStatus".equals(method.getName())) {
                            status.set((Integer) args[0]);
                            return null;
                        }
                        if ("sendError".equals(method.getName())) {
                            status.set((Integer) args[0]);
                            message.set(args.length > 1 ? (String) args[1] : null);
                            return null;
                        }
                        return defaultValue(method.getReturnType());
                    });
        }
    }
}
