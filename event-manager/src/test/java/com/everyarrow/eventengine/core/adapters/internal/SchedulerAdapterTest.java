package com.everyarrow.eventengine.core.adapters.internal;

import com.everyarrow.eventengine.core.services.runtime.EventManagerServiceIdentity;
import com.everyarrow.eventengine.core.services.runtime.ServiceCall;
import com.everyarrow.eventengine.core.services.runtime.ServiceCallSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchedulerAdapterTest {

    private String previousServiceKey;

    @BeforeEach
    void captureServiceKeyProperty() {
        previousServiceKey = System.getProperty(EventManagerServiceIdentity.API_KEY_CONFIG);
    }

    @AfterEach
    void restoreServiceKeyProperty() {
        if (previousServiceKey == null) {
            System.clearProperty(EventManagerServiceIdentity.API_KEY_CONFIG);
        } else {
            System.setProperty(EventManagerServiceIdentity.API_KEY_CONFIG, previousServiceKey);
        }
    }

    @Test
    void scheduledCallsCarryServiceWorkerCredentialWithoutChangingSource() {
        System.setProperty(EventManagerServiceIdentity.API_KEY_CONFIG, "local-service-key");

        ServiceCall call = SchedulerAdapter.newScheduledServiceCall(null, "marketing_boot.dispatcher.ensure");

        assertEquals("marketing_boot.dispatcher.ensure", call.getType());
        assertEquals(ServiceCallSource.SCHEDULE, call.getSource());
        assertEquals("local-service-key", call.getMetadata(EventManagerServiceIdentity.API_KEY_METADATA));
    }
}
