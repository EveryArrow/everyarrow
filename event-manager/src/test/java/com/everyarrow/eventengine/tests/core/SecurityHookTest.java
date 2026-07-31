/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.tests.core;

import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.eventengine.core.hooks.SecurityAccessDeniedException;
import com.everyarrow.eventengine.core.hooks.SecurityAuthenticationException;
import com.everyarrow.eventengine.core.hooks.SecurityAuthorizationResolver;
import com.everyarrow.eventengine.core.hooks.SecurityBearerTokenVerifier;
import com.everyarrow.eventengine.core.hooks.SecurityHook;
import com.everyarrow.eventengine.core.hooks.SecurityPrincipalResolver;
import com.everyarrow.eventengine.core.security.SecurityServiceContract;
import com.everyarrow.eventengine.core.services.runtime.SecurityContext;
import com.everyarrow.eventengine.core.services.runtime.ServiceCall;
import com.everyarrow.eventengine.core.services.runtime.ServiceCallSource;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.everyarrow.utilities.environment.Environment;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityHookTest {

    @AfterEach
    void resetEnvironment() {
        Environment.reset();
    }

    @Test
    void bearerTokenResolvesPrincipalAndInjectsAuthProviderId() {
        Environment.put("CONSOLE_ID", "security-app-123");

        SecurityHook hook = new SecurityHook(null, new FixedBearerTokenVerifier("oidc|principal-123"), new FakeResolver(
                new SecurityPrincipalResolver.ResolvedPrincipal("principal-1", "customer-1"),
                new SecurityPrincipalResolver.ResolvedPrincipal(null, null)),
                request -> true);

        ServiceCall call = new ServiceCall();
        call.setType(SecurityServiceContract.RESOLVE_PRINCIPAL);
        call.setSource(ServiceCallSource.REST);
        call.setParams(new JsonObject());
        call.putMetadata("http.authorization", "Bearer signed-token");

        hook.before(call);

        SecurityContext context = call.getSecurityContext();
        assertEquals(SecurityContext.CredentialType.BEARER, context.getCredentialType());
        assertEquals("oidc|principal-123", context.getAuthProviderId());
        assertEquals("oidc|principal-123", call.getParams().get("auth_provider_id").getAsString());
        assertEquals("principal-1", context.getPrincipalId());
        assertEquals("customer-1", context.getCustomerId());
        assertTrue(context.isAuthenticated());
        assertFalse(context.isSystemCall());
    }

    @Test
    void bearerTokenFallsBackToVerifiedEmailWhenSubjectIsNotLinkedLocally() {
        Environment.put("CONSOLE_ID", "security-app-123");

        SecurityHook hook = new SecurityHook(null,
                new FixedBearerTokenVerifier("oidc|principal-123", "oidc|principal-123", "alice@example.com"),
                new EmailFallbackResolver(
                        new SecurityPrincipalResolver.ResolvedPrincipal(null, null),
                        new SecurityPrincipalResolver.ResolvedPrincipal("principal-7", "customer-7"),
                        new SecurityPrincipalResolver.ResolvedPrincipal(null, null)),
                request -> true);

        ServiceCall call = new ServiceCall();
        call.setType(SecurityServiceContract.RESOLVE_PRINCIPAL);
        call.setSource(ServiceCallSource.REST);
        call.setParams(new JsonObject());
        call.putMetadata("http.authorization", "Bearer signed-token");

        hook.before(call);

        SecurityContext context = call.getSecurityContext();
        assertEquals("alice@example.com", context.getEmail());
        assertEquals("alice@example.com", call.getParams().get("email").getAsString());
        assertEquals("principal-7", context.getPrincipalId());
        assertEquals("customer-7", context.getCustomerId());
        assertTrue(context.isAuthenticated());
    }

    @Test
    void apiKeyHeaderResolvesPrincipalAndInjectsApiKey() {
        Environment.put("CONSOLE_ID", "security-app-123");

        SecurityHook hook = new SecurityHook(null, token -> new SecurityBearerTokenVerifier.VerifiedBearerToken("unused"), new FakeResolver(
                new SecurityPrincipalResolver.ResolvedPrincipal(null, null),
                new SecurityPrincipalResolver.ResolvedPrincipal("principal-2", "customer-2")),
                request -> true);

        ServiceCall call = new ServiceCall();
        call.setType(SecurityServiceContract.CHECK_API_KEY_ACCESS);
        call.setSource(ServiceCallSource.WEBSOCKET);
        JsonObject params = new JsonObject();
        params.addProperty("customer_id", "spoofed-customer");
        call.setParams(params);
        call.putMetadata("http.x-api-key", "api-key-123");

        hook.before(call);

        SecurityContext context = call.getSecurityContext();
        assertEquals(SecurityContext.CredentialType.API_KEY, context.getCredentialType());
        assertEquals("api-key-123", call.getParams().get("api_key").getAsString());
        assertEquals("principal-2", context.getPrincipalId());
        assertEquals("customer-2", context.getCustomerId());
        assertEquals("principal-2", call.getParams().get("principal_id").getAsString());
        assertEquals("customer-2", call.getParams().get("customer_id").getAsString());
        assertTrue(context.isAuthenticated());
    }

    @Test
    void externalSecretParamDoesNotActAsApiKey() {
        Environment.put("CONSOLE_ID", "security-app-123");

        SecurityHook hook = new SecurityHook(null, token -> new SecurityBearerTokenVerifier.VerifiedBearerToken("unused"), new FakeResolver(
                new SecurityPrincipalResolver.ResolvedPrincipal(null, null),
                new SecurityPrincipalResolver.ResolvedPrincipal("secret-principal-1", "secret-customer-1")),
                request -> true);

        JsonObject params = new JsonObject();
        params.addProperty("secret", "public-secret-123");

        ServiceCall call = new ServiceCall();
        call.setType(SecurityServiceContract.CHECK_API_KEY_ACCESS);
        call.setSource(ServiceCallSource.REST);
        call.setParams(params);

        SecurityAuthenticationException ex = assertThrows(SecurityAuthenticationException.class, () -> hook.before(call));

        assertEquals("Credentials required for service type: " + SecurityServiceContract.CHECK_API_KEY_ACCESS, ex.getMessage());
        assertEquals(SecurityContext.CredentialType.NONE, call.getSecurityContext().getCredentialType());
    }

    @Test
    void apiKeyAuthorizationResultCanResolvePrincipalWithoutSeparatePrincipalLookup() {
        Environment.put("BROWBOT_CONSOLE_ID", "browbot-app-123");

        PrincipalAuthorizationResolver authorizationResolver = new PrincipalAuthorizationResolver(
                new SecurityAuthorizationResolver.AuthorizationResult(
                        true,
                        true,
                        "browbot-principal-1",
                        "customer-1",
                        "browbot-app-123"));
        SecurityHook hook = new SecurityHook(null,
                token -> new SecurityBearerTokenVerifier.VerifiedBearerToken("unused"),
                new FailingPrincipalResolver(),
                authorizationResolver);

        ServiceCall call = new ServiceCall();
        call.setType("browbot.dispatch");
        call.setSource(ServiceCallSource.REST);
        call.setParams(new JsonObject());
        call.putMetadata("http.x-api-key", "browbot-key");
        call.putMetadata("http.customer-id", "customer-1");

        hook.before(call);

        SecurityContext context = call.getSecurityContext();
        assertEquals(SecurityContext.CredentialType.API_KEY, context.getCredentialType());
        assertEquals("browbot-key", call.getParams().get("api_key").getAsString());
        assertEquals("browbot-principal-1", context.getPrincipalId());
        assertEquals("customer-1", context.getCustomerId());
        assertEquals("browbot-app-123", context.getConsoleId());
        assertEquals("browbot-principal-1", call.getParams().get("principal_id").getAsString());
        assertEquals("customer-1", call.getParams().get("customer_id").getAsString());
        assertEquals("browbot-app-123", call.getParams().get("console_id").getAsString());
        assertEquals("customer-1", call.getParams().getAsJsonObject("_security").get("customer_id").getAsString());
        assertEquals("browbot-principal-1", call.getParams().getAsJsonObject("_security").get("principal_id").getAsString());
        assertEquals("api_key", call.getParams().getAsJsonObject("_security").get("credential_type").getAsString());
        assertTrue(context.isAuthenticated());
        assertNotNull(authorizationResolver.lastRequest);
        assertEquals("browbot-app-123", authorizationResolver.lastRequest.targetConsoleId());
        assertEquals("customer-1", authorizationResolver.lastRequest.customerIdHint());
    }

    @Test
    void bearerEveryArrowAdminBypassAllowsCallAndMarksRole() {
        Environment.put("PERCH_CONSOLE_ID", "perch-app-123");

        PrincipalAuthorizationResolver authorizationResolver = new PrincipalAuthorizationResolver(
                new SecurityAuthorizationResolver.AuthorizationResult(
                        true,
                        true,
                        "everyarrow-admin-principal-1",
                        "customer-1",
                        "perch-app-123",
                        true));
        SecurityHook hook = new SecurityHook(null, new FixedBearerTokenVerifier("oidc|principal-999"), new FakeResolver(
                new SecurityPrincipalResolver.ResolvedPrincipal("everyarrow-admin-principal-1", "customer-1"),
                new SecurityPrincipalResolver.ResolvedPrincipal(null, null)),
                authorizationResolver);

        ServiceCall call = new ServiceCall();
        call.setType("perch.telemetry.start_group");
        call.setSource(ServiceCallSource.REST);
        call.setParams(new JsonObject());
        call.putMetadata("http.authorization", "Bearer signed-token");

        hook.before(call);

        SecurityContext context = call.getSecurityContext();
        assertEquals("everyarrow-admin-principal-1", context.getPrincipalId());
        assertEquals("customer-1", context.getCustomerId());
        assertTrue(context.getRoles().contains(SecurityServiceContract.EVERYARROW_ADMIN_ROLE_KEY));
        assertTrue(context.isAuthenticated());
    }

    @Test
    void apiKeyEveryArrowAdminBypassHydratesPrincipalAndMarksRole() {
        Environment.put("BROWBOT_CONSOLE_ID", "browbot-app-123");

        PrincipalAuthorizationResolver authorizationResolver = new PrincipalAuthorizationResolver(
                new SecurityAuthorizationResolver.AuthorizationResult(
                        true,
                        true,
                        "everyarrow-admin-api-principal-1",
                        "customer-1",
                        "browbot-app-123",
                        true));
        SecurityHook hook = new SecurityHook(null,
                token -> new SecurityBearerTokenVerifier.VerifiedBearerToken("unused"),
                new FailingPrincipalResolver(),
                authorizationResolver);

        ServiceCall call = new ServiceCall();
        call.setType("browbot.dispatch");
        call.setSource(ServiceCallSource.REST);
        call.setParams(new JsonObject());
        call.putMetadata("http.x-api-key", "everyarrow-admin-key");

        hook.before(call);

        SecurityContext context = call.getSecurityContext();
        assertEquals(SecurityContext.CredentialType.API_KEY, context.getCredentialType());
        assertEquals("everyarrow-admin-api-principal-1", context.getPrincipalId());
        assertEquals("customer-1", context.getCustomerId());
        assertEquals("browbot-app-123", context.getConsoleId());
        assertTrue(context.getRoles().contains(SecurityServiceContract.EVERYARROW_ADMIN_ROLE_KEY));
        assertTrue(context.isAuthenticated());
    }

    @Test
    void internalCallWithoutCredentialsRemainsSystemCall() {
        SecurityHook hook = new SecurityHook(null, token -> new SecurityBearerTokenVerifier.VerifiedBearerToken("unused"), new FakeResolver(
                new SecurityPrincipalResolver.ResolvedPrincipal(null, null),
                new SecurityPrincipalResolver.ResolvedPrincipal(null, null)),
                request -> true);

        ServiceCall call = new ServiceCall();
        call.setType("engine.health");
        call.setSource(ServiceCallSource.INTERNAL);
        call.setParams(new JsonObject());

        hook.before(call);

        SecurityContext context = call.getSecurityContext();
        assertEquals(SecurityContext.CredentialType.NONE, context.getCredentialType());
        assertFalse(context.isAuthenticated());
        assertTrue(context.isSystemCall());
    }

    @Test
    void scheduledServiceWorkerApiKeyResolvesPrincipalWithoutExternalAuthorization() {
        SecurityHook hook = new SecurityHook(null,
                token -> new SecurityBearerTokenVerifier.VerifiedBearerToken("unused"),
                new FakeResolver(
                        new SecurityPrincipalResolver.ResolvedPrincipal(null, null),
                        new SecurityPrincipalResolver.ResolvedPrincipal("service-principal-1", "customer-1")),
                request -> {
                    throw new AssertionError("Scheduled service-worker calls should not use external authorization");
                });

        ServiceCall call = new ServiceCall();
        call.setType("marketing_boot.dispatcher.ensure");
        call.setSource(ServiceCallSource.SCHEDULE);
        call.setParams(new JsonObject());
        call.putMetadata("http.x-api-key", "service-worker-key");

        hook.before(call);

        SecurityContext context = call.getSecurityContext();
        assertEquals(SecurityContext.CredentialType.API_KEY, context.getCredentialType());
        assertEquals("service-principal-1", context.getPrincipalId());
        assertEquals("customer-1", context.getCustomerId());
        assertEquals("service-principal-1", call.getParams().get("principal_id").getAsString());
        assertEquals("customer-1", call.getParams().get("customer_id").getAsString());
        assertEquals("service-worker-key", call.getParams().get("api_key").getAsString());
        assertTrue(context.isAuthenticated());
        assertFalse(context.isSystemCall());
    }

    @Test
    void queueServiceWorkerApiKeyResolvesPrincipalWithoutExternalAuthorization() {
        Environment.put("EA_EVENT_MANAGER_SERVICE_API_KEY", "queue-service-worker-key");
        Environment.put("EA_EVENT_MANAGER_SERVICE_PRINCIPAL_ID", "queue-service-principal-1");

        SecurityHook hook = new SecurityHook(null,
                token -> new SecurityBearerTokenVerifier.VerifiedBearerToken("unused"),
                new FakeResolver(
                        new SecurityPrincipalResolver.ResolvedPrincipal(null, null),
                        new SecurityPrincipalResolver.ResolvedPrincipal(null, null)),
                request -> {
                    throw new AssertionError("Queue service-worker calls should not use external authorization");
                });

        ServiceCall call = new ServiceCall();
        call.setType("marketing_boot.dispatcher.ensure");
        call.setSource(ServiceCallSource.QUEUE);
        call.setParams(new JsonObject());
        call.putMetadata("http.x-api-key", "queue-service-worker-key");

        hook.before(call);

        SecurityContext context = call.getSecurityContext();
        assertEquals(SecurityContext.CredentialType.API_KEY, context.getCredentialType());
        assertEquals("queue-service-principal-1", context.getPrincipalId());
        assertEquals("queue-service-principal-1", call.getParams().get("principal_id").getAsString());
        assertFalse(call.getParams().has("user_id"));
        assertTrue(context.isAuthenticated());
    }

    @Test
    void queueCallWithoutServiceWorkerCredentialFailsClosed() {
        SecurityHook hook = new SecurityHook(null, new FixedBearerTokenVerifier("unused"), new FakeResolver(
                new SecurityPrincipalResolver.ResolvedPrincipal(null, null),
                new SecurityPrincipalResolver.ResolvedPrincipal(null, null)),
                request -> true);

        ServiceCall call = new ServiceCall();
        call.setType("marketing_boot.dispatcher.ensure");
        call.setSource(ServiceCallSource.QUEUE);
        call.setParams(new JsonObject());

        SecurityAuthenticationException ex = assertThrows(SecurityAuthenticationException.class, () -> hook.before(call));

        assertEquals("Credentials required for service type: marketing_boot.dispatcher.ensure", ex.getMessage());
    }

    @Test
    void internalSecurityResolverCallDoesNotResolveItsOwnApiKeyParam() {
        SecurityHook hook = new SecurityHook(null,
                token -> new SecurityBearerTokenVerifier.VerifiedBearerToken("unused"),
                new FailingPrincipalResolver(),
                request -> {
                    throw new AssertionError("Internal security resolver call should not authorize itself");
                });

        JsonObject params = new JsonObject();
        params.addProperty("api_key", "api-key-used-by-security-function");

        ServiceCall call = new ServiceCall();
        call.setType(SecurityServiceContract.RESOLVE_PRINCIPAL);
        call.setSource(ServiceCallSource.INTERNAL);
        call.setParams(params);
        call.putMetadata(SecurityHook.INTERNAL_SECURITY_CALL_METADATA, true);

        hook.before(call);

        SecurityContext context = call.getSecurityContext();
        assertEquals(SecurityContext.CredentialType.NONE, context.getCredentialType());
        assertTrue(context.isSystemCall());
    }

    @Test
    void bearerPrincipalDeniedWhenAuthorizationRejectsResolvedTarget() {
        Environment.put("PERCH_CONSOLE_ID", "perch-app-123");

        CapturingAuthorizationResolver authorizationResolver = new CapturingAuthorizationResolver(false);
        SecurityHook hook = new SecurityHook(null, new FixedBearerTokenVerifier("oidc|principal-999"), new FakeResolver(
                new SecurityPrincipalResolver.ResolvedPrincipal("principal-9", "customer-9"),
                new SecurityPrincipalResolver.ResolvedPrincipal(null, null)),
                authorizationResolver);

        ServiceCall call = new ServiceCall();
        call.setType("perch.telemetry.start_group");
        call.setSource(ServiceCallSource.REST);
        call.setParams(new JsonObject());
        call.putMetadata("http.authorization", "Bearer signed-token");

        SecurityAccessDeniedException ex = assertThrows(SecurityAccessDeniedException.class, () -> hook.before(call));

        assertEquals("Access denied for service type: perch.telemetry.start_group", ex.getMessage());
        assertNotNull(authorizationResolver.lastRequest);
        assertEquals("perch-app-123", authorizationResolver.lastRequest.targetConsoleId());
        assertEquals("service", authorizationResolver.lastRequest.securedObjectTypeName());
        assertEquals("perch.telemetry.start_group", authorizationResolver.lastRequest.serviceType());
        assertEquals("plugin:perch", authorizationResolver.lastRequest.securedItem());
        assertEquals("oidc|principal-999", authorizationResolver.lastRequest.authProviderId());
    }

    @Test
    void hostedBuiltInSurfaceUsesEventManagerConsoleIdAndPluginSecuredItem() {
        Environment.put("EVENT_MANAGER_CONSOLE_ID", "event-manager-app-123");
        Environment.put("PERCH_CONSOLE_ID", "perch-product-app-123");

        CapturingAuthorizationResolver authorizationResolver = new CapturingAuthorizationResolver(true);
        SecurityHook hook = new SecurityHook(null, new FixedBearerTokenVerifier("oidc|principal-999"), new FakeResolver(
                new SecurityPrincipalResolver.ResolvedPrincipal("principal-9", "customer-9"),
                new SecurityPrincipalResolver.ResolvedPrincipal(null, null)),
                authorizationResolver);

        ServiceCall call = new ServiceCall();
        call.setType("perch.telemetry.start_group");
        call.setSource(ServiceCallSource.REST);
        call.setParams(new JsonObject());
        call.putMetadata("http.authorization", "Bearer signed-token");

        hook.before(call);

        assertNotNull(authorizationResolver.lastRequest);
        assertEquals("event-manager-app-123", authorizationResolver.lastRequest.targetConsoleId());
        assertEquals("perch.telemetry.start_group", authorizationResolver.lastRequest.serviceType());
        assertEquals("plugin:perch", authorizationResolver.lastRequest.securedItem());
    }

    @Test
    void perchCanUseItsOwnConsoleIdWhenNotHostedByEventManager() {
        Environment.put("EA_EVENT_MANAGER_HOSTED_CONSOLE_KEYS", "SECURITY");
        Environment.put("PERCH_CONSOLE_ID", "perch-product-app-123");

        CapturingAuthorizationResolver authorizationResolver = new CapturingAuthorizationResolver(true);
        SecurityHook hook = new SecurityHook(null, new FixedBearerTokenVerifier("oidc|principal-999"), new FakeResolver(
                new SecurityPrincipalResolver.ResolvedPrincipal("principal-9", "customer-9"),
                new SecurityPrincipalResolver.ResolvedPrincipal(null, null)),
                authorizationResolver);

        ServiceCall call = new ServiceCall();
        call.setType("perch.telemetry.start_group");
        call.setSource(ServiceCallSource.REST);
        call.setParams(new JsonObject());
        call.putMetadata("http.authorization", "Bearer signed-token");

        hook.before(call);

        assertNotNull(authorizationResolver.lastRequest);
        assertEquals("perch-product-app-123", authorizationResolver.lastRequest.targetConsoleId());
    }

    @Test
    void consoleDispatchAuthorizesCanonicalInnerServiceType() {
        Environment.put("CONSOLE_ID", "console-app-123");

        Engine engine = new Engine();
        ServiceCallDefinition consoleDispatch = new ServiceCallDefinition();
        consoleDispatch.setServiceType("console_query_dispatch");
        ServiceCallDefinition.MonitoredMeta monitored = new ServiceCallDefinition.MonitoredMeta();
        monitored.application = "security";
        consoleDispatch.setMonitored(monitored);
        engine.getRegistry().registerDefinition(consoleDispatch);

        ServiceCallDefinition tapService = new ServiceCallDefinition();
        tapService.setServiceType("everyarrow.connector.create_customer_server");
        engine.getRegistry().registerDefinition(tapService);

        CapturingAuthorizationResolver authorizationResolver = new CapturingAuthorizationResolver(true);
        SecurityHook hook = new SecurityHook(engine, new FixedBearerTokenVerifier("oidc|principal-999"), new FakeResolver(
                new SecurityPrincipalResolver.ResolvedPrincipal("principal-9", "customer-9"),
                new SecurityPrincipalResolver.ResolvedPrincipal(null, null)),
                authorizationResolver);

        JsonObject params = new JsonObject();
        params.addProperty("type", "create_customer_server");

        ServiceCall call = new ServiceCall();
        call.setType("console_query_dispatch");
        call.setSource(ServiceCallSource.REST);
        call.setParams(params);
        call.putMetadata("http.authorization", "Bearer signed-token");

        hook.before(call);

        assertNotNull(authorizationResolver.lastRequest);
        assertEquals("console-app-123", authorizationResolver.lastRequest.targetConsoleId());
        assertEquals("everyarrow.connector.create_customer_server", authorizationResolver.lastRequest.securedItem());
    }

    @Test
    void consoleDispatchPrefersEveryArrowConsoleNamespace() {
        Environment.put("CONSOLE_ID", "console-app-123");

        Engine engine = new Engine();
        ServiceCallDefinition consoleDispatch = new ServiceCallDefinition();
        consoleDispatch.setServiceType("console_query_dispatch");
        ServiceCallDefinition.MonitoredMeta monitored = new ServiceCallDefinition.MonitoredMeta();
        monitored.application = "security";
        consoleDispatch.setMonitored(monitored);
        engine.getRegistry().registerDefinition(consoleDispatch);

        ServiceCallDefinition consoleService = new ServiceCallDefinition();
        consoleService.setServiceType("everyarrow.console.get_console");
        engine.getRegistry().registerDefinition(consoleService);

        CapturingAuthorizationResolver authorizationResolver = new CapturingAuthorizationResolver(true);
        SecurityHook hook = new SecurityHook(engine, new FixedBearerTokenVerifier("oidc|principal-999"), new FakeResolver(
                new SecurityPrincipalResolver.ResolvedPrincipal("principal-9", "customer-9"),
                new SecurityPrincipalResolver.ResolvedPrincipal(null, null)),
                authorizationResolver);

        JsonObject params = new JsonObject();
        params.addProperty("type", "get_console");

        ServiceCall call = new ServiceCall();
        call.setType("console_query_dispatch");
        call.setSource(ServiceCallSource.REST);
        call.setParams(params);
        call.putMetadata("http.authorization", "Bearer signed-token");

        hook.before(call);

        assertNotNull(authorizationResolver.lastRequest);
        assertEquals("everyarrow.console.get_console", authorizationResolver.lastRequest.securedItem());
    }

    @Test
    void serviceAccountBearerUsesAuthProviderIdForResolutionAndAuthorization() {
        Environment.put("MARKETING_CONSOLE_ID", "marketing-app-123");

        PrincipalAuthorizationResolver authorizationResolver = new PrincipalAuthorizationResolver(
                new SecurityAuthorizationResolver.AuthorizationResult(
                        true,
                        true,
                        "svc-principal-1",
                        "customer-1",
                        "marketing-app-123"));
        CapturingPrincipalResolver principalResolver = new CapturingPrincipalResolver(
                new SecurityPrincipalResolver.ResolvedPrincipal("svc-principal-1", "customer-1"),
                new SecurityPrincipalResolver.ResolvedPrincipal(null, null));
        SecurityHook hook = new SecurityHook(
                null,
                new FixedBearerTokenVerifier("service-account-marketing", "marketing-service-account-client"),
                principalResolver,
                authorizationResolver);

        ServiceCall call = new ServiceCall();
        call.setType("marketing.demo.publish_campaign");
        call.setSource(ServiceCallSource.REST);
        call.setParams(new JsonObject());
        call.putMetadata("http.authorization", "Bearer service-account-token");

        hook.before(call);

        SecurityContext context = call.getSecurityContext();
        assertNotNull(context);
        assertEquals(null, principalResolver.lastAuthProviderId);
        assertEquals("marketing-service-account-client", context.getAuthProviderId());
        assertEquals("marketing-service-account-client", call.getParams().get("auth_provider_id").getAsString());
        assertEquals("marketing-service-account-client", authorizationResolver.lastRequest.authProviderId());
        assertEquals("marketing-app-123", authorizationResolver.lastRequest.targetConsoleId());
        assertEquals(SecurityContext.CredentialType.BEARER, authorizationResolver.lastRequest.credentialType());
        assertEquals("svc-principal-1", context.getPrincipalId());
        assertTrue(context.isAuthenticated());
    }

    @Test
    void bearerPrincipalWithoutLocalUserFailsClosed() {
        Environment.put("PERCH_CONSOLE_ID", "perch-app-123");

        SecurityHook hook = new SecurityHook(null, new FixedBearerTokenVerifier("oidc|ghost-user"), new FakeResolver(
                new SecurityPrincipalResolver.ResolvedPrincipal(null, null),
                new SecurityPrincipalResolver.ResolvedPrincipal(null, null)),
                request -> true);

        ServiceCall call = new ServiceCall();
        call.setType("perch.telemetry.start_group");
        call.setSource(ServiceCallSource.REST);
        call.setParams(new JsonObject());
        call.putMetadata("http.authorization", "Bearer signed-token");

        SecurityAuthenticationException ex = assertThrows(SecurityAuthenticationException.class, () -> hook.before(call));

        assertEquals("Credential did not resolve to a local principal for service type: perch.telemetry.start_group", ex.getMessage());
    }

    @Test
    void apiKeyWithoutLocalUserFailsClosed() {
        Environment.put("CONSOLE_ID", "security-app-123");

        SecurityHook hook = new SecurityHook(null, token -> new SecurityBearerTokenVerifier.VerifiedBearerToken("unused"), new FakeResolver(
                new SecurityPrincipalResolver.ResolvedPrincipal(null, null),
                new SecurityPrincipalResolver.ResolvedPrincipal(null, null)),
                request -> true);

        ServiceCall call = new ServiceCall();
        call.setType(SecurityServiceContract.CHECK_API_KEY_ACCESS);
        call.setSource(ServiceCallSource.WEBSOCKET);
        call.setParams(new JsonObject());
        call.putMetadata("http.x-api-key", "api-key-404");

        SecurityAuthenticationException ex = assertThrows(SecurityAuthenticationException.class, () -> hook.before(call));

        assertEquals("Credential did not resolve to a local principal for service type: "
                + SecurityServiceContract.CHECK_API_KEY_ACCESS, ex.getMessage());
    }

    @Test
    void authenticatedCallWithoutResolvedPrincipalFailsClosed() {
        SecurityHook hook = new SecurityHook(null, new FixedBearerTokenVerifier("oidc|principal-123"), new FakeResolver(
                new SecurityPrincipalResolver.ResolvedPrincipal("principal-1", "customer-1"),
                new SecurityPrincipalResolver.ResolvedPrincipal(null, null)),
                request -> true);

        ServiceCall call = new ServiceCall();
        call.setType("perch.telemetry.start_group");
        call.setSource(ServiceCallSource.REST);
        call.setParams(new JsonObject());
        call.putMetadata("http.authorization", "Bearer signed-token");

        SecurityAuthenticationException ex = assertThrows(SecurityAuthenticationException.class, () -> hook.before(call));

        assertEquals("Credential did not resolve to a local principal for service type: perch.telemetry.start_group", ex.getMessage());
    }

    @Test
    void externalCallWithoutCredentialsFailsClosed() {
        SecurityHook hook = new SecurityHook(null, new FixedBearerTokenVerifier("unused"), new FakeResolver(
                new SecurityPrincipalResolver.ResolvedPrincipal(null, null),
                new SecurityPrincipalResolver.ResolvedPrincipal(null, null)),
                request -> true);

        ServiceCall call = new ServiceCall();
        call.setType("perch.telemetry.start_group");
        call.setSource(ServiceCallSource.REST);
        call.setParams(new JsonObject());

        SecurityAuthenticationException ex = assertThrows(SecurityAuthenticationException.class, () -> hook.before(call));

        assertEquals("Credentials required for service type: perch.telemetry.start_group", ex.getMessage());
    }

    @Test
    void aggregateDispatchRouteWithoutCredentialsFailsClosed() {
        Engine engine = new Engine();
        registerProtectedAggregateDispatch(engine, "console_query_dispatch");
        registerProtectedAggregateDispatch(engine, "foundry_query_dispatch");

        SecurityHook hook = new SecurityHook(engine, new FixedBearerTokenVerifier("unused"), new FakeResolver(
                new SecurityPrincipalResolver.ResolvedPrincipal(null, null),
                new SecurityPrincipalResolver.ResolvedPrincipal(null, null)),
                request -> true);

        assertAggregateDispatchRequiresCredentials(hook, "console_query_dispatch", "get_console");
        assertAggregateDispatchRequiresCredentials(hook, "foundry_query_dispatch", "get_foundry_projects");
    }

    @Test
    void publicServiceTypeAllowlistPermitsUnauthenticatedExternalCall() {
        Environment.put("EA_SECURITY_PUBLIC_SERVICE_TYPES", "marketing.demo.book_demo");

        SecurityHook hook = new SecurityHook(null, new FixedBearerTokenVerifier("unused"), new FakeResolver(
                new SecurityPrincipalResolver.ResolvedPrincipal(null, null),
                new SecurityPrincipalResolver.ResolvedPrincipal(null, null)),
                request -> true);

        ServiceCall call = new ServiceCall();
        call.setType("marketing.demo.book_demo");
        call.setSource(ServiceCallSource.REST);
        call.setParams(new JsonObject());

        hook.before(call);

        SecurityContext context = call.getSecurityContext();
        assertTrue(context.isPublicRoute());
        assertFalse(context.isAuthenticated());
    }

    private void assertAggregateDispatchRequiresCredentials(SecurityHook hook, String serviceType, String innerType) {
        JsonObject params = new JsonObject();
        params.addProperty("type", innerType);
        params.add("params", new JsonObject());

        ServiceCall call = new ServiceCall();
        call.setType(serviceType);
        call.setSource(ServiceCallSource.REST);
        call.setParams(params);

        SecurityAuthenticationException ex = assertThrows(SecurityAuthenticationException.class, () -> hook.before(call));

        assertEquals("Credentials required for service type: " + serviceType, ex.getMessage());
    }

    private void registerProtectedAggregateDispatch(Engine engine, String serviceType) {
        ServiceCallDefinition definition = new ServiceCallDefinition();
        definition.setServiceType(serviceType);
        ServiceCallDefinition.Annotations annotations = new ServiceCallDefinition.Annotations();
        ServiceCallDefinition.CallMeta callMeta = new ServiceCallDefinition.CallMeta();
        callMeta.open = false;
        annotations.call = callMeta;
        ServiceCallDefinition.SecuredMeta secured = new ServiceCallDefinition.SecuredMeta();
        secured.isPublic = false;
        annotations.secured = secured;
        definition.setAnnotations(annotations);
        engine.getRegistry().registerDefinition(definition);
    }

    @Test
    void publicRegistryMetadataPermitsUnauthenticatedExternalCall() {
        Engine engine = new Engine();

        ServiceCallDefinition definition = new ServiceCallDefinition();
        definition.setServiceType("marketing.demo.book_demo");
        ServiceCallDefinition.Annotations annotations = new ServiceCallDefinition.Annotations();
        ServiceCallDefinition.SecuredMeta secured = new ServiceCallDefinition.SecuredMeta();
        secured.isPublic = true;
        annotations.secured = secured;
        definition.setAnnotations(annotations);
        engine.getRegistry().registerDefinition(definition);

        SecurityHook hook = new SecurityHook(engine, new FixedBearerTokenVerifier("unused"), new FakeResolver(
                new SecurityPrincipalResolver.ResolvedPrincipal(null, null),
                new SecurityPrincipalResolver.ResolvedPrincipal(null, null)),
                request -> true);

        ServiceCall call = new ServiceCall();
        call.setType("marketing.demo.book_demo");
        call.setSource(ServiceCallSource.REST);
        call.setParams(new JsonObject());

        hook.before(call);

        SecurityContext context = call.getSecurityContext();
        assertTrue(context.isPublicRoute());
        assertFalse(context.isAuthenticated());
    }

    @Test
    void publicCallMetadataPermitsUnauthenticatedExternalCall() {
        Engine engine = new Engine();

        ServiceCallDefinition definition = new ServiceCallDefinition();
        definition.setServiceType("marketing.demo.book_demo");
        ServiceCallDefinition.Annotations annotations = new ServiceCallDefinition.Annotations();
        ServiceCallDefinition.CallMeta callMeta = new ServiceCallDefinition.CallMeta();
        callMeta.open = true;
        annotations.call = callMeta;
        definition.setAnnotations(annotations);
        engine.getRegistry().registerDefinition(definition);

        SecurityHook hook = new SecurityHook(engine, new FixedBearerTokenVerifier("unused"), new FakeResolver(
                new SecurityPrincipalResolver.ResolvedPrincipal(null, null),
                new SecurityPrincipalResolver.ResolvedPrincipal(null, null)),
                request -> true);

        ServiceCall call = new ServiceCall();
        call.setType("marketing.demo.book_demo");
        call.setSource(ServiceCallSource.REST);
        call.setParams(new JsonObject());

        hook.before(call);

        SecurityContext context = call.getSecurityContext();
        assertTrue(context.isPublicRoute());
        assertFalse(context.isAuthenticated());
    }

    @Test
    void publicCallMetadataResolvesScheduledServiceApiKeyWithoutAuthorization() {
        Engine engine = new Engine();

        ServiceCallDefinition definition = new ServiceCallDefinition();
        definition.setServiceType("marketing_boot.superflow.schedule.everyarrow_smoke");
        ServiceCallDefinition.Annotations annotations = new ServiceCallDefinition.Annotations();
        ServiceCallDefinition.CallMeta callMeta = new ServiceCallDefinition.CallMeta();
        callMeta.open = true;
        annotations.call = callMeta;
        definition.setAnnotations(annotations);
        engine.getRegistry().registerDefinition(definition);

        SecurityHook hook = new SecurityHook(engine, new FixedBearerTokenVerifier("unused"), new FakeResolver(
                new SecurityPrincipalResolver.ResolvedPrincipal(null, null),
                new SecurityPrincipalResolver.ResolvedPrincipal("service-principal-1", "customer-1")),
                request -> {
                    throw new AssertionError("Public scheduled calls should not require authorization");
                });

        ServiceCall call = new ServiceCall();
        call.setType("marketing_boot.superflow.schedule.everyarrow_smoke");
        call.setSource(ServiceCallSource.SCHEDULE);
        call.setParams(new JsonObject());
        call.putMetadata("http.x-api-key", "local-service-worker-key");

        hook.before(call);

        SecurityContext context = call.getSecurityContext();
        assertTrue(context.isPublicRoute());
        assertEquals(SecurityContext.CredentialType.API_KEY, context.getCredentialType());
        assertEquals("service-principal-1", call.getParams().get("principal_id").getAsString());
        assertEquals("customer-1", call.getParams().get("customer_id").getAsString());
        assertFalse(call.getParams().has("user_id"));
        assertTrue(context.isAuthenticated());
    }

    @Test
    void configuredServiceWorkerPrincipalIdAuthenticatesScheduledCallWhenPrincipalIsNotResolved() {
        Environment.put("EA_EVENT_MANAGER_SERVICE_API_KEY", "configured-service-key");
        Environment.put("EA_EVENT_MANAGER_SERVICE_PRINCIPAL_ID", "25515994-e3f3-440a-ae42-dcf67e76d7eb");

        Engine engine = new Engine();
        ServiceCallDefinition definition = new ServiceCallDefinition();
        definition.setServiceType("marketing_boot.superflow.schedule.everyarrow_smoke");
        ServiceCallDefinition.Annotations annotations = new ServiceCallDefinition.Annotations();
        ServiceCallDefinition.CallMeta callMeta = new ServiceCallDefinition.CallMeta();
        callMeta.open = true;
        annotations.call = callMeta;
        definition.setAnnotations(annotations);
        engine.getRegistry().registerDefinition(definition);

        SecurityHook hook = new SecurityHook(engine, new FixedBearerTokenVerifier("unused"),
                new FakeResolver(
                        new SecurityPrincipalResolver.ResolvedPrincipal(null, null),
                        new SecurityPrincipalResolver.ResolvedPrincipal(null, null)),
                request -> {
                    throw new AssertionError("Configured service worker calls should not require authorization");
                });

        ServiceCall call = new ServiceCall();
        call.setType("marketing_boot.superflow.schedule.everyarrow_smoke");
        call.setSource(ServiceCallSource.SCHEDULE);
        call.setParams(new JsonObject());
        call.putMetadata("http.x-api-key", "configured-service-key");

        hook.before(call);

        SecurityContext context = call.getSecurityContext();
        assertTrue(context.isAuthenticated());
        assertEquals("25515994-e3f3-440a-ae42-dcf67e76d7eb", context.getPrincipalId());
        assertEquals("25515994-e3f3-440a-ae42-dcf67e76d7eb", call.getParams().get("principal_id").getAsString());
        assertFalse(call.getParams().has("user_id"));
        assertEquals(null, context.getCustomerId());
    }

    @Test
    void callMetadataApplicationKeySelectsAuthorizationTarget() {
        Environment.put("PLUGIN_APP_CONSOLE_ID", "plugin-app-123");

        Engine engine = new Engine();
        ServiceCallDefinition definition = new ServiceCallDefinition();
        definition.setServiceType("custom.namespace.run");
        ServiceCallDefinition.Annotations annotations = new ServiceCallDefinition.Annotations();
        ServiceCallDefinition.CallMeta callMeta = new ServiceCallDefinition.CallMeta();
        callMeta.applicationKey = "PLUGIN_APP";
        annotations.call = callMeta;
        definition.setAnnotations(annotations);
        engine.getRegistry().registerDefinition(definition);

        CapturingAuthorizationResolver authorizationResolver = new CapturingAuthorizationResolver(true);
        SecurityHook hook = new SecurityHook(engine, new FixedBearerTokenVerifier("oidc|principal-123"), new FakeResolver(
                new SecurityPrincipalResolver.ResolvedPrincipal("principal-1", "customer-1"),
                new SecurityPrincipalResolver.ResolvedPrincipal(null, null)),
                authorizationResolver);

        ServiceCall call = new ServiceCall();
        call.setType("custom.namespace.run");
        call.setSource(ServiceCallSource.REST);
        call.setParams(new JsonObject());
        call.putMetadata("http.authorization", "Bearer signed-token");

        hook.before(call);

        assertNotNull(authorizationResolver.lastRequest);
        assertEquals("plugin-app-123", authorizationResolver.lastRequest.targetConsoleId());
    }

    @Test
    void invalidBearerTokenFailsAuthentication() {
        SecurityHook hook = new SecurityHook(null,
                token -> {
                    throw new SecurityAuthenticationException("Bearer token verification failed");
                },
                new FakeResolver(
                        new SecurityPrincipalResolver.ResolvedPrincipal(null, null),
                        new SecurityPrincipalResolver.ResolvedPrincipal(null, null)),
                request -> true);

        ServiceCall call = new ServiceCall();
        call.setType("perch.telemetry.start_group");
        call.setSource(ServiceCallSource.REST);
        call.setParams(new JsonObject());
        call.putMetadata("http.authorization", "Bearer broken-token");

        SecurityAuthenticationException ex = assertThrows(SecurityAuthenticationException.class, () -> hook.before(call));

        assertEquals("Bearer token verification failed", ex.getMessage());
    }

    private static final class FakeResolver implements SecurityPrincipalResolver {

        private final ResolvedPrincipal bearerPrincipal;
        private final ResolvedPrincipal apiKeyPrincipal;

        private FakeResolver(ResolvedPrincipal bearerPrincipal, ResolvedPrincipal apiKeyPrincipal) {
            this.bearerPrincipal = bearerPrincipal;
            this.apiKeyPrincipal = apiKeyPrincipal;
        }

        @Override
        public ResolvedPrincipal resolveByAuthProviderId(String authProviderId) {
            return bearerPrincipal;
        }

        @Override
        public ResolvedPrincipal resolveByEmail(String email) {
            return new ResolvedPrincipal(null, null);
        }

        @Override
        public ResolvedPrincipal resolveByApiKey(String apiKey) {
            return apiKeyPrincipal;
        }
    }

    private static final class CapturingPrincipalResolver implements SecurityPrincipalResolver {

        private final ResolvedPrincipal bearerPrincipal;
        private final ResolvedPrincipal apiKeyPrincipal;
        private String lastAuthProviderId;

        private CapturingPrincipalResolver(ResolvedPrincipal bearerPrincipal, ResolvedPrincipal apiKeyPrincipal) {
            this.bearerPrincipal = bearerPrincipal;
            this.apiKeyPrincipal = apiKeyPrincipal;
        }

        @Override
        public ResolvedPrincipal resolveByAuthProviderId(String authProviderId) {
            this.lastAuthProviderId = authProviderId;
            return bearerPrincipal;
        }

        @Override
        public ResolvedPrincipal resolveByEmail(String email) {
            return new ResolvedPrincipal(null, null);
        }

        @Override
        public ResolvedPrincipal resolveByApiKey(String apiKey) {
            return apiKeyPrincipal;
        }
    }

    private static final class CapturingAuthorizationResolver implements SecurityAuthorizationResolver {

        private final boolean authorized;
        private AuthorizationRequest lastRequest;

        private CapturingAuthorizationResolver(boolean authorized) {
            this.authorized = authorized;
        }

        @Override
        public boolean isAuthorized(AuthorizationRequest request) {
            return authorize(request).authorized();
        }

        @Override
        public SecurityAuthorizationResolver.AuthorizationResult authorize(AuthorizationRequest request) {
            this.lastRequest = request;
            return new SecurityAuthorizationResolver.AuthorizationResult(
                    authorized,
                    true,
                    "authorized-principal-1",
                    "authorized-customer-1",
                    request == null ? null : request.targetConsoleId());
        }
    }

    private static final class PrincipalAuthorizationResolver implements SecurityAuthorizationResolver {

        private final SecurityAuthorizationResolver.AuthorizationResult result;
        private SecurityAuthorizationResolver.AuthorizationRequest lastRequest;

        private PrincipalAuthorizationResolver(SecurityAuthorizationResolver.AuthorizationResult result) {
            this.result = result;
        }

        @Override
        public boolean isAuthorized(SecurityAuthorizationResolver.AuthorizationRequest request) {
            return authorize(request).authorized();
        }

        @Override
        public SecurityAuthorizationResolver.AuthorizationResult authorize(
                SecurityAuthorizationResolver.AuthorizationRequest request) {
            this.lastRequest = request;
            return result;
        }
    }

    private static final class FailingPrincipalResolver implements SecurityPrincipalResolver {

        @Override
        public ResolvedPrincipal resolveByAuthProviderId(String authProviderId) {
            throw new AssertionError("Internal security resolver call should not resolve by auth provider id");
        }

        @Override
        public ResolvedPrincipal resolveByEmail(String email) {
            throw new AssertionError("Internal security resolver call should not resolve by email");
        }

        @Override
        public ResolvedPrincipal resolveByApiKey(String apiKey) {
            throw new AssertionError("Internal security resolver call should not resolve by api key");
        }
    }

    private static final class FixedBearerTokenVerifier implements SecurityBearerTokenVerifier {

        private final String subject;
        private final String authProviderId;
        private final String email;

        private FixedBearerTokenVerifier(String subject) {
            this(subject, subject, null);
        }

        private FixedBearerTokenVerifier(String subject, String authProviderId) {
            this(subject, authProviderId, null);
        }

        private FixedBearerTokenVerifier(String subject, String authProviderId, String email) {
            this.subject = subject;
            this.authProviderId = authProviderId;
            this.email = email;
        }

        @Override
        public VerifiedBearerToken verify(String token) {
            return new VerifiedBearerToken(subject, authProviderId, null, null, email);
        }
    }

    private static final class EmailFallbackResolver implements SecurityPrincipalResolver {

        private final ResolvedPrincipal byAuthProviderId;
        private final ResolvedPrincipal byEmail;
        private final ResolvedPrincipal byApiKey;

        private EmailFallbackResolver(ResolvedPrincipal byAuthProviderId,
                                      ResolvedPrincipal byEmail,
                                      ResolvedPrincipal byApiKey) {
            this.byAuthProviderId = byAuthProviderId;
            this.byEmail = byEmail;
            this.byApiKey = byApiKey;
        }

        @Override
        public ResolvedPrincipal resolveByAuthProviderId(String authProviderId) {
            return byAuthProviderId;
        }

        @Override
        public ResolvedPrincipal resolveByEmail(String email) {
            return byEmail;
        }

        @Override
        public ResolvedPrincipal resolveByApiKey(String apiKey) {
            return byApiKey;
        }
    }
}
