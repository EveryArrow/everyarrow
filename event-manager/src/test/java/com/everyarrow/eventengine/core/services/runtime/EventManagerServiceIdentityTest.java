package com.everyarrow.eventengine.core.services.runtime;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventManagerServiceIdentityTest {

    @Test
    void reportsMissingKeyWithoutSecretLeakage() {
        EventManagerServiceIdentity identity = new EventManagerServiceIdentity(key -> "");

        JsonObject readiness = identity.readinessJson();

        assertFalse(identity.isConfigured());
        assertEquals(EventManagerServiceIdentity.API_KEY_CONFIG, readiness.get("configKey").getAsString());
        assertFalse(readiness.get("configured").getAsBoolean());
        assertFalse(readiness.has("apiKey"));
    }

    @Test
    void appliesConfiguredKeyToInternalServiceCallMetadata() {
        EventManagerServiceIdentity identity = new EventManagerServiceIdentity(key -> " service-key ");
        ServiceCall call = new ServiceCall();

        identity.apply(call);

        assertTrue(identity.isConfigured());
        assertEquals(ServiceCallSource.INTERNAL, call.getSource());
        assertEquals("service-key", call.getMetadata(EventManagerServiceIdentity.API_KEY_METADATA));
    }

    @Test
    void appliesCredentialWithoutChangingExistingSource() {
        EventManagerServiceIdentity identity = new EventManagerServiceIdentity(key -> " service-key ");
        ServiceCall call = new ServiceCall();
        call.setSource(ServiceCallSource.SCHEDULE);

        identity.applyCredential(call);

        assertEquals(ServiceCallSource.SCHEDULE, call.getSource());
        assertEquals("service-key", call.getMetadata(EventManagerServiceIdentity.API_KEY_METADATA));
    }

    @Test
    void rejectsServiceCallsWhenKeyIsMissing() {
        EventManagerServiceIdentity identity = new EventManagerServiceIdentity(key -> null);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> identity.apply(new ServiceCall()));

        assertTrue(ex.getMessage().contains(EventManagerServiceIdentity.API_KEY_CONFIG));
    }
}
