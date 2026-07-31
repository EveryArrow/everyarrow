package com.everyarrow.eventengine.core.hooks;

public interface SecurityPrincipalResolver {

    ResolvedPrincipal resolveByAuthProviderId(String authProviderId);

    ResolvedPrincipal resolveByEmail(String email);

    ResolvedPrincipal resolveByApiKey(String apiKey);

    record ResolvedPrincipal(String principalId, String clientId, String customerId, String customerPrincipalId) {

        public ResolvedPrincipal(String principalId, String clientId, String customerId) {
            this(principalId, clientId, customerId, null);
        }

        public ResolvedPrincipal(String principalId, String customerId) {
            this(principalId, null, customerId);
        }

        public boolean isResolved() {
            return principalId != null && !principalId.isBlank();
        }
    }
}
