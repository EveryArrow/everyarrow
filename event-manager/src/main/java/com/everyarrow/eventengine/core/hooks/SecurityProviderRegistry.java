/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.hooks;

import java.util.Objects;

/**
 * Runtime boundary through which an optional Security plugin supplies
 * credential verification, principal resolution, and authorization.
 *
 * With no installed provider every protected call fails closed, while public
 * calls remain available through {@link SecurityHook}.
 */
public final class SecurityProviderRegistry implements
        SecurityBearerTokenVerifier,
        SecurityPrincipalResolver,
        SecurityAuthorizationResolver {

    private volatile Provider provider = Provider.failClosed();

    public void install(SecurityBearerTokenVerifier bearerTokenVerifier,
                        SecurityPrincipalResolver principalResolver,
                        SecurityAuthorizationResolver authorizationResolver) {
        provider = new Provider(
                Objects.requireNonNull(bearerTokenVerifier, "bearerTokenVerifier is required"),
                Objects.requireNonNull(principalResolver, "principalResolver is required"),
                Objects.requireNonNull(authorizationResolver, "authorizationResolver is required"),
                true);
    }

    public void clear() {
        provider = Provider.failClosed();
    }

    public boolean isInstalled() {
        return provider.installed();
    }

    @Override
    public VerifiedBearerToken verify(String token) {
        return provider.bearerTokenVerifier().verify(token);
    }

    @Override
    public ResolvedPrincipal resolveByAuthProviderId(String authProviderId) {
        return provider.principalResolver().resolveByAuthProviderId(authProviderId);
    }

    @Override
    public ResolvedPrincipal resolveByEmail(String email) {
        return provider.principalResolver().resolveByEmail(email);
    }

    @Override
    public ResolvedPrincipal resolveByApiKey(String apiKey) {
        return provider.principalResolver().resolveByApiKey(apiKey);
    }

    @Override
    public boolean isAuthorized(AuthorizationRequest request) {
        return provider.authorizationResolver().isAuthorized(request);
    }

    @Override
    public AuthorizationResult authorize(AuthorizationRequest request) {
        return provider.authorizationResolver().authorize(request);
    }

    private record Provider(
            SecurityBearerTokenVerifier bearerTokenVerifier,
            SecurityPrincipalResolver principalResolver,
            SecurityAuthorizationResolver authorizationResolver,
            boolean installed) {

        private static Provider failClosed() {
            SecurityBearerTokenVerifier bearer = token -> {
                throw new SecurityAuthenticationException("No Security provider is installed");
            };
            SecurityPrincipalResolver principals = new SecurityPrincipalResolver() {
                @Override
                public ResolvedPrincipal resolveByAuthProviderId(String authProviderId) {
                    return unresolved();
                }

                @Override
                public ResolvedPrincipal resolveByEmail(String email) {
                    return unresolved();
                }

                @Override
                public ResolvedPrincipal resolveByApiKey(String apiKey) {
                    return unresolved();
                }

                private ResolvedPrincipal unresolved() {
                    return new ResolvedPrincipal(null, null, null, null);
                }
            };
            SecurityAuthorizationResolver authorization = request -> false;
            return new Provider(bearer, principals, authorization, false);
        }
    }
}
