/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.hooks;

import com.everyarrow.eventengine.core.services.runtime.SecurityContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityProviderRegistryTest {

    @Test
    void missingProviderFailsClosed() {
        SecurityProviderRegistry registry = new SecurityProviderRegistry();

        assertFalse(registry.isInstalled());
        assertThrows(SecurityAuthenticationException.class, () -> registry.verify("token"));
        assertFalse(registry.resolveByApiKey("key").isResolved());
        assertFalse(registry.isAuthorized(request()));
    }

    @Test
    void installedProviderOwnsAllSecurityDecisions() {
        SecurityProviderRegistry registry = new SecurityProviderRegistry();
        registry.install(
                token -> new SecurityBearerTokenVerifier.VerifiedBearerToken("principal"),
                new FixedPrincipalResolver(),
                request -> true);

        assertTrue(registry.isInstalled());
        assertTrue(registry.resolveByApiKey("key").isResolved());
        assertTrue(registry.isAuthorized(request()));

        registry.clear();
        assertFalse(registry.isInstalled());
        assertFalse(registry.isAuthorized(request()));
    }

    private SecurityAuthorizationResolver.AuthorizationRequest request() {
        return new SecurityAuthorizationResolver.AuthorizationRequest(
                "protected.service",
                "protected.service",
                "service",
                "console",
                SecurityContext.CredentialType.API_KEY,
                null,
                null,
                "key");
    }

    private static final class FixedPrincipalResolver implements SecurityPrincipalResolver {
        @Override
        public ResolvedPrincipal resolveByAuthProviderId(String authProviderId) {
            return resolved();
        }

        @Override
        public ResolvedPrincipal resolveByEmail(String email) {
            return resolved();
        }

        @Override
        public ResolvedPrincipal resolveByApiKey(String apiKey) {
            return resolved();
        }

        private ResolvedPrincipal resolved() {
            return new ResolvedPrincipal("principal", "client", "customer", null);
        }
    }
}
