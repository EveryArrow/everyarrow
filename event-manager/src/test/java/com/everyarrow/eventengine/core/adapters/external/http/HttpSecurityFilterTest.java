package com.everyarrow.eventengine.core.adapters.external.http;

import com.everyarrow.utilities.environment.Environment;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpSecurityFilterTest {

    @AfterEach
    void resetEnvironment() {
        Environment.reset();
    }

    @Test
    void appliesHeadersAndHstsOnlyToSecureRequests() throws Exception {
        CapturedResponse insecure = new CapturedResponse();
        new HttpSecurityFilter(new HttpRequestRateLimiter(10, 10))
                .doFilter(request(false, "127.0.0.1"), insecure.proxy(), countingChain());

        assertEquals("nosniff", insecure.headers.get("X-Content-Type-Options"));
        assertEquals("DENY", insecure.headers.get("X-Frame-Options"));
        assertEquals("no-store", insecure.headers.get("Cache-Control"));
        assertFalse(insecure.headers.containsKey("Strict-Transport-Security"));

        CapturedResponse secure = new CapturedResponse();
        new HttpSecurityFilter(new HttpRequestRateLimiter(10, 10))
                .doFilter(request(true, "127.0.0.1"), secure.proxy(), countingChain());

        assertEquals(
                "max-age=31536000; includeSubDomains",
                secure.headers.get("Strict-Transport-Security"));
    }

    @Test
    void rejectsRequestsBeyondTheConfiguredPeerLimit() throws Exception {
        HttpSecurityFilter filter =
                new HttpSecurityFilter(new HttpRequestRateLimiter(1, 10));
        AtomicInteger calls = new AtomicInteger();
        FilterChain chain = (request, response) -> calls.incrementAndGet();

        filter.doFilter(request(false, "192.0.2.10"), new CapturedResponse().proxy(), chain);
        CapturedResponse rejected = new CapturedResponse();
        filter.doFilter(request(false, "192.0.2.10"), rejected.proxy(), chain);

        assertEquals(1, calls.get());
        assertEquals(429, rejected.status);
        assertTrue(rejected.headers.containsKey("Retry-After"));
        assertEquals("{\"error\":\"Too many requests\"}", rejected.body.toString());
    }

    @Test
    void invalidConfigurationUsesBoundedDefaults() {
        Environment.put(HttpSecurityFilter.MAX_REQUESTS_CONFIG, "invalid");
        Environment.put(HttpSecurityFilter.MAX_CLIENTS_CONFIG, "-1");

        assertEquals(
                HttpSecurityFilter.DEFAULT_MAX_REQUESTS,
                HttpSecurityFilter.configuredPositiveInt(
                        HttpSecurityFilter.MAX_REQUESTS_CONFIG,
                        HttpSecurityFilter.DEFAULT_MAX_REQUESTS));
        assertEquals(
                HttpSecurityFilter.DEFAULT_MAX_CLIENTS,
                HttpSecurityFilter.configuredPositiveInt(
                        HttpSecurityFilter.MAX_CLIENTS_CONFIG,
                        HttpSecurityFilter.DEFAULT_MAX_CLIENTS));
    }

    private FilterChain countingChain() {
        return (request, response) -> {
        };
    }

    private HttpServletRequest request(boolean secure, String remoteAddress) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "isSecure" -> secure;
                    case "getRemoteAddr" -> remoteAddress;
                    default -> defaultValue(method.getReturnType());
                });
    }

    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }

    private final class CapturedResponse {
        private final Map<String, String> headers = new HashMap<>();
        private final StringWriter body = new StringWriter();
        private int status = 200;

        private HttpServletResponse proxy() {
            return (HttpServletResponse) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[]{HttpServletResponse.class},
                    (proxy, method, arguments) -> {
                        switch (method.getName()) {
                            case "setHeader" ->
                                    headers.put((String) arguments[0], (String) arguments[1]);
                            case "setStatus" -> status = (int) arguments[0];
                            case "getWriter" -> {
                                return new PrintWriter(body, true);
                            }
                            default -> {
                                return defaultValue(method.getReturnType());
                            }
                        }
                        return null;
                    });
        }
    }
}
