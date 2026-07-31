package com.everyarrow.eventengine.core.hooks;

import com.everyarrow.eventengine.core.services.runtime.SecurityContext;

public interface SecurityAuthorizationResolver {

    boolean isAuthorized(AuthorizationRequest request);

    default AuthorizationResult authorize(AuthorizationRequest request) {
        boolean authorized = isAuthorized(request);
        boolean authenticated = authorized
                || request != null && request.credentialType() == SecurityContext.CredentialType.API_KEY;
        return new AuthorizationResult(authorized, authenticated, null, null, null);
    }

    record AuthorizationRequest(
            String serviceType,
            String securedItem,
            String securedObjectTypeName,
            String targetConsoleId,
            SecurityContext.CredentialType credentialType,
            String authProviderId,
            String email,
            String apiKey,
            String customerIdHint) {

        public AuthorizationRequest(String serviceType,
                                    String securedItem,
                                    String securedObjectTypeName,
                                    String targetConsoleId,
                                    SecurityContext.CredentialType credentialType,
                                    String authProviderId,
                                    String email,
                                    String apiKey) {
            this(serviceType,
                    securedItem,
                    securedObjectTypeName,
                    targetConsoleId,
                    credentialType,
                    authProviderId,
                    email,
                    apiKey,
                    null);
        }
    }

    record AuthorizationResult(
            boolean authorized,
            boolean authenticated,
            String principalId,
            String clientId,
            String customerId,
            String customerPrincipalId,
            String consoleId,
            boolean everyArrowAdmin) {

        public AuthorizationResult(boolean authorized,
                                   boolean authenticated,
                                   String principalId,
                                   String customerId,
                                   String consoleId,
                                   boolean everyArrowAdmin) {
            this(authorized, authenticated, principalId, null, customerId, null, consoleId, everyArrowAdmin);
        }

        public AuthorizationResult(boolean authorized,
                                   boolean authenticated,
                                   String principalId,
                                   String clientId,
                                   String customerId,
                                   String consoleId,
                                   boolean everyArrowAdmin) {
            this(authorized, authenticated, principalId, clientId, customerId, null, consoleId, everyArrowAdmin);
        }

        public AuthorizationResult(boolean authorized,
                                   boolean authenticated,
                                   String principalId,
                                   String customerId,
                                   String consoleId) {
            this(authorized, authenticated, principalId, null, customerId, null, consoleId, false);
        }

        public boolean hasPrincipal() {
            return principalId != null && !principalId.isBlank()
                    && customerId != null && !customerId.isBlank();
        }
    }
}
