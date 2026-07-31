package com.everyarrow.eventengine.tests.http;

import com.everyarrow.eventengine.core.adapters.external.http.CorsPolicyResolver;
import com.everyarrow.utilities.environment.Environment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorsPolicyResolverTest {

    private final CorsPolicyResolver resolver = new CorsPolicyResolver();

    @AfterEach
    void resetCorsOrigins() {
        Environment.put("EA_ALLOWED_CORS_ORIGINS", "");
        Environment.put("EA_ALLOW_ANY_CORS_ORIGIN", "false");
    }

    @Test
    void loopbackOriginsAreAllowedWithoutConfiguration() {
        assertTrue(resolver.isOriginAllowed("http://localhost:3000"));
        assertTrue(resolver.isOriginAllowed("https://127.0.0.1:5173"));
    }

    @Test
    void configuredOriginsAreAllowed() {
        Environment.put("EA_ALLOWED_CORS_ORIGINS", "https://marketing.everyarrow.io, https://console.everyarrow.io");

        assertTrue(resolver.isOriginAllowed("https://marketing.everyarrow.io"));
        assertTrue(resolver.isOriginAllowed("https://console.everyarrow.io"));
    }

    @Test
    void wildcardIsIgnoredWithoutExplicitOptIn() {
        Environment.put("EA_ALLOWED_CORS_ORIGINS", "*");

        assertFalse(resolver.isOriginAllowed("https://external.example.com"));
    }

    @Test
    void wildcardAllowsAnyOriginOnlyWithExplicitOptIn() {
        Environment.put("EA_ALLOWED_CORS_ORIGINS", "*");
        Environment.put("EA_ALLOW_ANY_CORS_ORIGIN", "true");

        assertTrue(resolver.isOriginAllowed("https://external.example.com"));
    }

    @Test
    void unknownNonLoopbackOriginIsRejected() {
        Environment.put("EA_ALLOWED_CORS_ORIGINS", "https://marketing.everyarrow.io");

        assertFalse(resolver.isOriginAllowed("https://evil.example.com"));
    }
}
