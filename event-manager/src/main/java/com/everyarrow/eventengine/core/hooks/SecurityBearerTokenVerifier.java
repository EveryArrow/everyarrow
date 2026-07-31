package com.everyarrow.eventengine.core.hooks;

public interface SecurityBearerTokenVerifier {

    VerifiedBearerToken verify(String token);

    record VerifiedBearerToken(String subject,
                               String authProviderId,
                               String clientId,
                               String preferredUsername,
                               String email) {

        public VerifiedBearerToken(String subject) {
            this(subject, subject, null, null, null);
        }
    }
}