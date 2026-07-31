package com.everyarrow.eventengine.core.hooks;

import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.eventengine.core.config.EventManagerOperatingMode;
import com.everyarrow.eventengine.core.security.EveryArrowServicePrefixes;
import com.everyarrow.eventengine.core.security.SecurityApplicationTargets;
import com.everyarrow.eventengine.core.security.SecurityServiceContract;
import com.everyarrow.eventengine.core.services.runtime.SecurityContext;
import com.everyarrow.eventengine.core.services.runtime.ServiceCall;
import com.everyarrow.eventengine.core.services.runtime.ServiceCallSource;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.everyarrow.utilities.environment.Environment;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Codex: Validates tokens and enforces @Secured annotations before handler execution.
 */
public class SecurityHook implements ServiceHook {
    private static final Logger LOG = Logger.getLogger(SecurityHook.class.getName());

    public static final String INTERNAL_SECURITY_CALL_METADATA = "security.internalCall";
    private static final String EVERYARROW_ADMIN_ROLE_KEY = SecurityServiceContract.EVERYARROW_ADMIN_ROLE_KEY;
    private static final String SERVICE_API_KEY_CONFIG = "EA_EVENT_MANAGER_SERVICE_API_KEY";
    private static final String SERVICE_PRINCIPAL_ID_CONFIG = "EA_EVENT_MANAGER_SERVICE_PRINCIPAL_ID";
    private static final String SERVICE_CLIENT_ID_CONFIG = "EA_EVENT_MANAGER_SERVICE_CLIENT_ID";
    private static final String SERVICE_CUSTOMER_ID_CONFIG = "EA_EVENT_MANAGER_SERVICE_CUSTOMER_ID";
    private static final String SERVICE_CONSOLE_ID_CONFIG = "EVENT_MANAGER_CONSOLE_ID";
    private static final String SERVICE_AUTH_PROVIDER_ID = "service-worker:event-manager";
    private static final Set<String> AUTHENTICATED_SELF_RESOLUTION_SERVICE_TYPES = Set.of(
            SecurityServiceContract.CHECK_API_KEY_ACCESS,
            SecurityServiceContract.RESOLVE_CUSTOMER_SCOPE,
            SecurityServiceContract.RESOLVE_PRINCIPAL
    );

    private final Engine engine;
    private final SecurityBearerTokenVerifier bearerTokenVerifier;
    private final SecurityPrincipalResolver principalResolver;
    private final SecurityAuthorizationResolver authorizationResolver;

    public SecurityHook(Engine engine) {
        this(engine, new SecurityProviderRegistry());
    }

    public SecurityHook(Engine engine, SecurityProviderRegistry securityProviders) {
        this(engine,
                Objects.requireNonNull(securityProviders, "securityProviders is required"),
                securityProviders,
                securityProviders);
    }

    public SecurityHook(Engine engine,
                        SecurityBearerTokenVerifier bearerTokenVerifier,
                        SecurityPrincipalResolver principalResolver,
                        SecurityAuthorizationResolver authorizationResolver) {
        this.engine = engine;
        this.bearerTokenVerifier = bearerTokenVerifier;
        this.principalResolver = principalResolver;
        this.authorizationResolver = authorizationResolver;
    }

    @Override
    public void before(ServiceCall call) {
        if (call == null) {
            return;
        }

        SecurityContext context = call.getSecurityContext();
        if (context == null) {
            context = new SecurityContext();
            call.setSecurityContext(context);
        }

        context.setServiceType(call.getType());
        context.setCallSource(call.getSource());
        if (isInternalSecurityCall(call)) {
            context.setCredentialType(SecurityContext.CredentialType.NONE);
            context.setSystemCall(true);
            return;
        }

        populateTargetMetadata(call, context);
        populatePrincipalHints(call, context);
        resolveConfiguredServiceWorker(call, context);
        if (context.isPublicRoute()) {
            if (!isLocalMode()) {
                resolvePrincipal(call, context);
            }
            injectResolvedPrincipalParams(call, context);
            markSystemCall(call, context);
            return;
        }
        enforceExternalCredentialRequirement(call, context);
        if (isLocalMode() && hasPresentedCredential(context) && !context.isAuthenticated()) {
            throw new SecurityAuthenticationException(
                    "Database-backed credential resolution is unavailable while "
                            + EventManagerOperatingMode.CONFIG + "=local");
        }
        if (shouldAuthorizeBeforePrincipalResolution(context)) {
            authorize(call, context);
        } else {
            resolvePrincipal(call, context);
            authorize(call, context);
        }
        injectResolvedPrincipalParams(call, context);

        markSystemCall(call, context);
    }

    @Override
    public void after(ServiceCall call, Object result) {
        // Reserved for post-auth audit enrichment.
    }

    private void populateTargetMetadata(ServiceCall call, SecurityContext context) {
        context.setPublicRoute(isPublicRoute(call, null));

        if (engine == null || engine.getRegistry() == null || call.getType() == null || call.getType().isBlank()) {
            return;
        }

        ServiceCallDefinition def = engine.getRegistry().getDefinition(call.getType());
        if (def == null) {
            return;
        }

        context.setPublicRoute(isPublicRoute(call, def));

        context.setDefinitionSource(def.getSource());
        if (def.getAnnotations() == null) {
            return;
        }

        if (def.getAnnotations().call != null && !isBlank(def.getAnnotations().call.applicationKey)) {
            context.setTargetApplication(def.getAnnotations().call.applicationKey);
        } else if (def.getAnnotations().monitored != null) {
            context.setTargetApplication(def.getAnnotations().monitored.application);
        }

        if (def.getAnnotations().perch != null && !isBlank(def.getAnnotations().perch.systemName)) {
            context.setTargetSystem(def.getAnnotations().perch.systemName);
        } else if (def.getAnnotations().monitored != null) {
            context.setTargetSystem(def.getAnnotations().monitored.system);
        }
    }

    private void populatePrincipalHints(ServiceCall call, SecurityContext context) {
        context.setCredentialType(resolveCredentialType(call));

        if (context.getCredentialType() == SecurityContext.CredentialType.BEARER) {
            SecurityBearerTokenVerifier.VerifiedBearerToken verifiedBearerToken = verifyBearerToken(call);
            context.setAuthProviderId(verifiedBearerToken.authProviderId());
            boolean keepCredentialHintsOutOfParams = keepCredentialHintsOutOfParams(call);
            if (!keepCredentialHintsOutOfParams) {
                call.getParams().addProperty("auth_provider_id", verifiedBearerToken.authProviderId());
            }
            if (!isBlank(verifiedBearerToken.email())) {
                context.setEmail(verifiedBearerToken.email());
                if (!keepCredentialHintsOutOfParams) {
                    call.getParams().addProperty("email", verifiedBearerToken.email());
                }
            }
        }

        JsonObject params = call.getParams();
        if (params != null) {
            if (context.getCredentialType() == SecurityContext.CredentialType.API_KEY
                    && !keepCredentialHintsOutOfParams(call)
                    && !params.has("api_key")) {
                String apiKey = extractApiKey(call);
                if (!isBlank(apiKey)) {
                    params.addProperty("api_key", apiKey);
                }
            }
            if (allowsParamIdentityHints(call)) {
                context.setPrincipalId(stringParam(params, "principal_id"));
                context.setConsoleId(stringParam(params, "console_id"));
                if (isBlank(context.getEmail())) {
                    context.setEmail(stringParam(params, "email"));
                }
                if (context.getCredentialType() != SecurityContext.CredentialType.BEARER
                        && isBlank(context.getAuthProviderId())) {
                    context.setAuthProviderId(stringParam(params, "auth_provider_id"));
                }
            }
        }
    }

    private boolean keepCredentialHintsOutOfParams(ServiceCall call) {
        String serviceType = safeServiceType(call);
        return serviceType.startsWith("foundry.");
    }

    private void resolvePrincipal(ServiceCall call, SecurityContext context) {
        if (principalResolver == null || context == null || context.isAuthenticated()) {
            return;
        }

        SecurityPrincipalResolver.ResolvedPrincipal principal = null;
        if (context.getCredentialType() == SecurityContext.CredentialType.BEARER) {
            if (!isBlank(context.getAuthProviderId())) {
                principal = principalResolver.resolveByAuthProviderId(context.getAuthProviderId());
            }
            if ((principal == null || !principal.isResolved()) && !isBlank(context.getEmail())) {
                principal = principalResolver.resolveByEmail(context.getEmail());
            }
        } else if (context.getCredentialType() == SecurityContext.CredentialType.API_KEY) {
            String apiKey = extractApiKey(call);
            if (apiKey != null) {
                principal = principalResolver.resolveByApiKey(apiKey);
                JsonObject params = call.getParams();
                if (params != null && !params.has("api_key")) {
                    params.addProperty("api_key", apiKey);
                }
            }
        }

        if (principal == null || !principal.isResolved()) {
            if (hasPresentedCredential(context)) {
                LOG.warning(() -> "[security] principal resolution failed"
                        + " serviceType=" + safeServiceType(call)
                        + " credentialType=" + context.getCredentialType()
                        + " authProviderId=" + safeLogValue(context.getAuthProviderId())
                        + " email=" + safeLogValue(context.getEmail())
                        + " clientId=" + safeLogValue(context.getClientId()));
                throw new SecurityAuthenticationException(
                        "Credential did not resolve to a local principal for service type: " + safeServiceType(call));
            }
            return;
        }

        context.setPrincipalId(principal.principalId());
        context.setClientId(principal.clientId());
        context.setCustomerId(principal.customerId());
        context.setCustomerPrincipalId(principal.customerPrincipalId());
        context.setAuthenticated(true);
    }

    private void authorize(ServiceCall call, SecurityContext context) {
        if (context != null && context.isPublicRoute()) {
            return;
        }

        if (context == null) {
            return;
        }

        if (isAuthenticatedRuntimeServiceWorker(context)) {
            return;
        }

        if (!context.isAuthenticated() && !hasPresentedCredential(context)) {
            return;
        }

        if (isAuthenticatedSelfResolutionService(call)) {
            return;
        }

        if (authorizationResolver == null) {
            throw new SecurityAccessDeniedException(
                    "Authorization resolver is not configured for service type: " + safeServiceType(call));
        }

        String targetConsoleId = resolveTargetConsoleId(call, context);

        String serviceType = safeServiceType(call);
        String securedItem = resolveSecuredItem(call, context);
        if (isBlank(securedItem)) {
            throw new SecurityAccessDeniedException("Authorization target is not configured for service type: unknown");
        }

        String apiKey = context.getCredentialType() == SecurityContext.CredentialType.API_KEY
                ? extractApiKey(call)
                : null;
        SecurityAuthorizationResolver.AuthorizationRequest request =
                new SecurityAuthorizationResolver.AuthorizationRequest(
                        serviceType,
                        securedItem,
                        resolveSecuredObjectTypeName(),
                        targetConsoleId,
                        context.getCredentialType(),
                        context.getAuthProviderId(),
                        context.getEmail(),
                        apiKey,
                        resolveCustomerIdHint(call));

        SecurityAuthorizationResolver.AuthorizationResult result = authorizationResolver.authorize(request);
        if (result == null) {
            result = new SecurityAuthorizationResolver.AuthorizationResult(false, false, null, null, null, null, false);
        }
        applyAuthorizationPrincipal(context, result);
        if (result.everyArrowAdmin()) {
            context.addRole(EVERYARROW_ADMIN_ROLE_KEY);
        }

        if (!result.authenticated() && !context.isAuthenticated()) {
            throw new SecurityAuthenticationException(
                    "Credential did not resolve to a local principal for service type: " + serviceType);
        }

        if (!result.authorized()) {
            throw new SecurityAccessDeniedException("Access denied for service type: " + serviceType);
        }

        if (!context.isAuthenticated()) {
            throw new SecurityAuthenticationException(
                    "Credential did not resolve to a local principal for service type: " + serviceType);
        }
    }

    private void enforceExternalCredentialRequirement(ServiceCall call, SecurityContext context) {
        if (context == null || context.isPublicRoute() || !isExternalSource(context.getCallSource())) {
            return;
        }

        if (!hasPresentedCredential(context)) {
            throw new SecurityAuthenticationException(
                    "Credentials required for service type: " + safeServiceType(call));
        }
    }

    private SecurityContext.CredentialType resolveCredentialType(ServiceCall call) {
        String authorization = stringValue(call.getMetadata("http.authorization"));
        if (authorization != null) {
            if (authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
                return SecurityContext.CredentialType.BEARER;
            }
            if (authorization.regionMatches(true, 0, "ApiKey ", 0, 7)
                    || authorization.regionMatches(true, 0, "Api-Key ", 0, 8)) {
                return SecurityContext.CredentialType.API_KEY;
            }
        }

        String xApiKey = stringValue(call.getMetadata("http.x-api-key"));
        if (xApiKey != null) {
            return SecurityContext.CredentialType.API_KEY;
        }

        JsonObject params = call.getParams();
        if (params != null && allowsInlineApiKey(call)) {
            String inlineApiKey = stringParam(params, "_api_key");
            if (inlineApiKey == null) {
                inlineApiKey = stringParam(params, "api_key");
            }
            if (inlineApiKey == null) {
                inlineApiKey = stringParam(params, "secret");
            }
            if (inlineApiKey != null) {
                return SecurityContext.CredentialType.API_KEY;
            }
        }

        return SecurityContext.CredentialType.NONE;
    }

    private SecurityBearerTokenVerifier.VerifiedBearerToken verifyBearerToken(ServiceCall call) {
        if (bearerTokenVerifier == null) {
            throw new SecurityAuthenticationException("Bearer token verifier is not configured");
        }

        String token = extractBearerToken(call);
        SecurityBearerTokenVerifier.VerifiedBearerToken verified = bearerTokenVerifier.verify(token);
        String authProviderId = verified == null ? null : verified.authProviderId();
        if (isBlank(authProviderId)) {
            throw new SecurityAuthenticationException("Bearer token missing auth provider id");
        }
        return verified;
    }

    private String extractBearerToken(ServiceCall call) {
        String authorization = stringValue(call.getMetadata("http.authorization"));
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new SecurityAuthenticationException("Missing bearer token");
        }

        String token = authorization.substring(7).trim();
        if (token.isEmpty()) {
            throw new SecurityAuthenticationException("Missing bearer token");
        }
        return token;
    }

    private String extractApiKey(ServiceCall call) {
        String headerValue = stringValue(call.getMetadata("http.x-api-key"));
        if (!isBlank(headerValue)) {
            return headerValue;
        }

        String authorization = stringValue(call.getMetadata("http.authorization"));
        if (!isBlank(authorization)) {
            if (authorization.regionMatches(true, 0, "ApiKey ", 0, 7)) {
                return authorization.substring(7).trim();
            }
            if (authorization.regionMatches(true, 0, "Api-Key ", 0, 8)) {
                return authorization.substring(8).trim();
            }
        }

        if (allowsInlineApiKey(call)) {
            JsonObject params = call.getParams();
            String inlineApiKey = stringParam(params, "_api_key");
            if (inlineApiKey != null) {
                return inlineApiKey;
            }
            inlineApiKey = stringParam(params, "api_key");
            if (inlineApiKey != null) {
                return inlineApiKey;
            }
            return stringParam(params, "secret");
        }

        return null;
    }

    private void injectResolvedPrincipalParams(ServiceCall call, SecurityContext context) {
        if (call == null || context == null || !context.isAuthenticated()) {
            return;
        }

        JsonObject params = call.getParams();
        if (params == null) {
            return;
        }

        putIfNotBlank(params, "principal_id", context.getPrincipalId());
        putIfNotBlank(params, "client_id", context.getClientId());
        putIfNotBlank(params, "customer_id", context.getCustomerId());
        putIfNotBlank(params, "customer_principal_id", context.getCustomerPrincipalId());
        putIfNotBlank(params, "console_id", context.getConsoleId());
        params.add("_security", securityPayload(context));
    }

    private JsonObject securityPayload(SecurityContext context) {
        JsonObject security = new JsonObject();
        security.addProperty("authenticated", context.isAuthenticated());
        security.addProperty("credential_type", credentialTypeName(context));
        security.addProperty("everyarrow_admin", context.getRoles().contains(EVERYARROW_ADMIN_ROLE_KEY));
        putIfNotBlank(security, "principal_id", context.getPrincipalId());
        putIfNotBlank(security, "client_id", context.getClientId());
        putIfNotBlank(security, "customer_id", context.getCustomerId());
        putIfNotBlank(security, "customer_principal_id", context.getCustomerPrincipalId());
        putIfNotBlank(security, "console_id", context.getConsoleId());

        JsonArray roles = new JsonArray();
        for (String role : context.getRoles()) {
            roles.add(role);
        }
        security.add("role_keys", roles);
        return security;
    }

    private String credentialTypeName(SecurityContext context) {
        if (context == null || context.getCredentialType() == null) {
            return "none";
        }
        if (context.getCredentialType() == SecurityContext.CredentialType.BEARER) {
            return "ux_session";
        }
        if (context.getCredentialType() == SecurityContext.CredentialType.API_KEY) {
            return "api_key";
        }
        return "none";
    }

    private void putIfNotBlank(JsonObject params, String key, String value) {
        if (params != null && key != null && !isBlank(value)) {
            params.addProperty(key, value);
        }
    }

    private void markSystemCall(ServiceCall call, SecurityContext context) {
        if (call == null || context == null) {
            return;
        }
        boolean systemCall = context.getCredentialType() == SecurityContext.CredentialType.NONE
                && call.getSource() == ServiceCallSource.INTERNAL;
        context.setSystemCall(systemCall);
    }

    private String resolveTargetConsoleId(ServiceCall call, SecurityContext context) {
        if (context != null && !isBlank(context.getTargetConsoleId())) {
            return context.getTargetConsoleId();
        }

        String applicationKey = resolveTargetApplicationKey(call, context);
        if (isBlank(applicationKey)) {
            return null;
        }

        String consoleId = SecurityApplicationTargets.resolveConsoleId(applicationKey, Environment.getInstance());

        if (context != null) {
            context.setTargetConsoleId(consoleId);
        }
        return consoleId;
    }

    private String resolveTargetApplicationKey(ServiceCall call, SecurityContext context) {
        if (context != null && !isBlank(context.getTargetApplication())) {
            return SecurityApplicationTargets.normalizeApplicationKey(context.getTargetApplication());
        }

        String serviceType = call == null ? null : call.getType();
        if (serviceType == null) {
            return null;
        }

        serviceType = serviceType.trim();
        if (serviceType.isEmpty()) {
            return null;
        }

        int separatorIndex = serviceType.indexOf('.');
        String namespace = separatorIndex >= 0 ? serviceType.substring(0, separatorIndex) : serviceType;
        return SecurityApplicationTargets.normalizeApplicationKey(namespace);
    }

    private boolean shouldAuthorizeBeforePrincipalResolution(SecurityContext context) {
        return context != null
                && (context.getCredentialType() == SecurityContext.CredentialType.API_KEY
                        || context.getCredentialType() == SecurityContext.CredentialType.BEARER)
                && context.getCallSource() != ServiceCallSource.SCHEDULE
                && context.getCallSource() != ServiceCallSource.QUEUE
                && !context.isPublicRoute()
                && !isAuthenticatedSelfResolutionService(context.getServiceType());
    }

    private boolean isAuthenticatedRuntimeServiceWorker(SecurityContext context) {
        return context != null
                && (context.getCallSource() == ServiceCallSource.SCHEDULE
                        || context.getCallSource() == ServiceCallSource.INTERNAL
                        || context.getCallSource() == ServiceCallSource.QUEUE)
                && context.getCredentialType() == SecurityContext.CredentialType.API_KEY
                && context.isAuthenticated();
    }

    private void resolveConfiguredServiceWorker(ServiceCall call, SecurityContext context) {
        if (call == null || context == null || context.isAuthenticated()
                || context.getCredentialType() != SecurityContext.CredentialType.API_KEY
                || (context.getCallSource() != ServiceCallSource.SCHEDULE
                        && context.getCallSource() != ServiceCallSource.INTERNAL
                        && context.getCallSource() != ServiceCallSource.QUEUE)) {
            return;
        }

        String configuredApiKey = runtimeConfig(SERVICE_API_KEY_CONFIG);
        String presentedApiKey = extractApiKey(call);
        if (isBlank(configuredApiKey) || isBlank(presentedApiKey)
                || !configuredApiKey.trim().equals(presentedApiKey.trim())) {
            return;
        }

        SecurityPrincipalResolver.ResolvedPrincipal servicePrincipal = isLocalMode() || principalResolver == null
                ? null
                : principalResolver.resolveByApiKey(presentedApiKey);

        String principalId = firstNonBlank(
                servicePrincipal == null ? null : servicePrincipal.principalId(),
                runtimeConfig(SERVICE_PRINCIPAL_ID_CONFIG));
        if (isBlank(principalId)) {
            return;
        }

        JsonObject params = call.getParams();
        context.setPrincipalId(principalId);
        context.setClientId(firstNonBlank(
                servicePrincipal == null ? null : servicePrincipal.clientId(),
                runtimeConfig(SERVICE_CLIENT_ID_CONFIG)));
        context.setCustomerId(firstNonBlank(
                servicePrincipal == null ? null : servicePrincipal.customerId(),
                runtimeConfig(SERVICE_CUSTOMER_ID_CONFIG)));
        context.setCustomerPrincipalId(servicePrincipal == null ? null : servicePrincipal.customerPrincipalId());
        context.setConsoleId(firstNonBlank(
                context.getConsoleId(),
                stringParam(params, "console_id"),
                runtimeConfig(SERVICE_CONSOLE_ID_CONFIG)));
        if (isBlank(context.getAuthProviderId())) {
            context.setAuthProviderId(SERVICE_AUTH_PROVIDER_ID);
        }
        context.setAuthenticated(true);
    }

    private void applyAuthorizationPrincipal(SecurityContext context,
                                             SecurityAuthorizationResolver.AuthorizationResult result) {
        if (context == null || result == null || !result.hasPrincipal()) {
            return;
        }

        context.setPrincipalId(result.principalId());
        context.setClientId(result.clientId());
        context.setCustomerId(result.customerId());
        context.setCustomerPrincipalId(result.customerPrincipalId());
        if (!isBlank(result.consoleId())) {
            context.setConsoleId(result.consoleId());
            context.setTargetConsoleId(result.consoleId());
        }
        context.setAuthenticated(true);
    }

    private String resolveCustomerIdHint(ServiceCall call) {
        if (call == null) {
            return null;
        }

        JsonObject params = call.getParams();
        return firstNonBlank(
                stringValue(call.getMetadata("http.customer-id")),
                stringParam(params, "customer_id_hint"),
                stringParam(params, "customer_id"));
    }

    private String resolveSecuredItem(ServiceCall call, SecurityContext context) {
        if (call == null) {
            return null;
        }

        String serviceType = call.getType();
        if (serviceType != null && serviceType.endsWith("_dispatch")) {
            JsonObject params = call.getParams();
            String innerType = stringParam(params, "type");
            if (!isBlank(innerType)) {
                return resolveCanonicalDispatchServiceType(innerType);
            }
        }

        String applicationKey = resolveTargetApplicationKey(call, context);
        return SecurityApplicationTargets.resolveSecuredItem(serviceType, applicationKey, Environment.getInstance());
    }

    private String resolveCanonicalDispatchServiceType(String innerType) {
        if (isBlank(innerType)) {
            return innerType;
        }

        String trimmed = innerType.trim();
        if (trimmed.contains(".")) {
            return trimmed;
        }

        String directMatch = serviceTypeIfRegistered(trimmed);
        if (!isBlank(directMatch)) {
            return directMatch;
        }

        String[] conventionalCandidates = {
                SecurityServiceContract.SERVICE_PREFIX + "." + trimmed,
                EveryArrowServicePrefixes.CONSOLE + "." + trimmed,
                EveryArrowServicePrefixes.REGISTRY + "." + trimmed,
                EveryArrowServicePrefixes.CONNECTOR + "." + trimmed,
                EveryArrowServicePrefixes.DITTOE_SWARM_PIPELINE + "." + trimmed,
                EveryArrowServicePrefixes.PERCH_TELEMETRY + "." + trimmed,
                EveryArrowServicePrefixes.EVENT_MANAGER_API + "." + trimmed,
                EveryArrowServicePrefixes.ENTITLEMENT + "." + trimmed
        };
        for (String candidate : conventionalCandidates) {
            String registered = serviceTypeIfRegistered(candidate);
            if (!isBlank(registered)) {
                return registered;
            }
        }

        return uniqueRegisteredServiceTypeEndingWith("." + trimmed, trimmed);
    }

    private String serviceTypeIfRegistered(String serviceType) {
        if (engine == null || engine.getRegistry() == null || isBlank(serviceType)) {
            return null;
        }
        return engine.getRegistry().getDefinition(serviceType) == null ? null : serviceType;
    }

    private String uniqueRegisteredServiceTypeEndingWith(String suffix, String fallback) {
        if (engine == null || engine.getRegistry() == null || isBlank(suffix)) {
            return fallback;
        }

        String match = null;
        for (ServiceCallDefinition definition : engine.getRegistry().listDefinitions()) {
            if (definition == null || isBlank(definition.getServiceType())) {
                continue;
            }

            String registeredServiceType = definition.getServiceType();
            if (!registeredServiceType.endsWith(suffix)) {
                continue;
            }

            if (match != null && !match.equals(registeredServiceType)) {
                return fallback;
            }
            match = registeredServiceType;
        }

        return isBlank(match) ? fallback : match;
    }

    private String resolveSecuredObjectTypeName() {
        return Environment.getInstance().get("EA_SECURITY_SECURED_OBJECT_TYPE_NAME", "service");
    }

    private boolean isAuthenticatedSelfResolutionService(ServiceCall call) {
        if (call == null || isBlank(call.getType())) {
            return false;
        }

        return isAuthenticatedSelfResolutionService(call.getType());
    }

    private boolean isAuthenticatedSelfResolutionService(String serviceType) {
        if (isBlank(serviceType)) {
            return false;
        }

        return AUTHENTICATED_SELF_RESOLUTION_SERVICE_TYPES.contains(serviceType);
    }

    private boolean isPublicRoute(ServiceCall call, ServiceCallDefinition def) {
        if (def != null && def.getAnnotations() != null && def.getAnnotations().secured != null
                && def.getAnnotations().secured.isPublic) {
            return true;
        }
        if (def != null && def.getAnnotations() != null && def.getAnnotations().call != null
                && def.getAnnotations().call.open) {
            return true;
        }

        if (call == null) {
            return false;
        }

        Set<String> publicServiceTypes = resolvePublicServiceTypes();
        String serviceType = call.getType();
        if (!isBlank(serviceType) && publicServiceTypes.contains(serviceType)) {
            return true;
        }

        String securedItem = resolveSecuredItem(call, null);
        if (!isBlank(securedItem) && publicServiceTypes.contains(securedItem)) {
            return true;
        }

        // Dynamic database policy belongs to the optional Security provider.
        // Without explicit public metadata or configuration, fail closed.
        return false;
    }

    private Set<String> resolvePublicServiceTypes() {
        String configured = Environment.getInstance().get("EA_SECURITY_PUBLIC_SERVICE_TYPES", "");
        if (configured == null || configured.isBlank()) {
            return Set.of();
        }

        Set<String> serviceTypes = new LinkedHashSet<>();
        Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .forEach(serviceTypes::add);
        return serviceTypes;
    }

    private String runtimeConfig(String key) {
        String configured = Environment.getInstance().get(key, "");
        if (!isBlank(configured)) {
            return configured;
        }
        return System.getProperty(key, "");
    }

    private boolean isExternalSource(ServiceCallSource source) {
        return source == ServiceCallSource.REST
                || source == ServiceCallSource.WEBSOCKET
                || source == ServiceCallSource.WEBHOOK
                || source == ServiceCallSource.QUEUE;
    }

    private boolean allowsInlineApiKey(ServiceCall call) {
        return call != null && call.getSource() == ServiceCallSource.INTERNAL;
    }

    private boolean allowsParamIdentityHints(ServiceCall call) {
        return call != null
                && (call.getSource() == ServiceCallSource.INTERNAL
                        || call.getSource() == ServiceCallSource.SCHEDULE);
    }

    private boolean isInternalSecurityCall(ServiceCall call) {
        if (call == null || call.getSource() != ServiceCallSource.INTERNAL) {
            return false;
        }

        Object marker = call.getMetadata(INTERNAL_SECURITY_CALL_METADATA);
        return Boolean.TRUE.equals(marker) || "true".equalsIgnoreCase(stringValue(marker));
    }

    private boolean hasPresentedCredential(SecurityContext context) {
        return context != null && context.getCredentialType() != SecurityContext.CredentialType.NONE;
    }

    private boolean isLocalMode() {
        return EventManagerOperatingMode.fromEnvironment().isLocal();
    }

    private String safeServiceType(ServiceCall call) {
        if (call == null || isBlank(call.getType())) {
            return "unknown";
        }
        return call.getType();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String stringParam(JsonObject params, String key) {
        if (params == null || key == null || !params.has(key) || params.get(key).isJsonNull()) {
            return null;
        }

        try {
            return params.get(key).getAsString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }

        String asString = value.toString();
        return asString.isBlank() ? null : asString;
    }

    private String safeLogValue(String value) {
        return isBlank(value) ? "<blank>" : value;
    }
}
