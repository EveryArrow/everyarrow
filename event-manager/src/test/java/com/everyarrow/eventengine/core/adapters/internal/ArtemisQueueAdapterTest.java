package com.everyarrow.eventengine.core.adapters.internal;

import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.everyarrow.utilities.environment.Environment;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArtemisQueueAdapterTest {

    @AfterEach
    void resetEnvironment() {
        Environment.reset();
    }

    @Test
    void queueMessageMayNameRegisteredServiceType() {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("serviceType", "tests.queue.registered");

        String serviceType = adapterWithDefinitions("tests.queue.registered")
                .resolveServiceTypeForMessage("tests.queue.registered", envelope);

        assertEquals("tests.queue.registered", serviceType);
    }

    @Test
    void queueMessageServiceTypeOverrideFailsClosedByDefault() {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("serviceType", "tests.queue.other");

        SecurityException ex = assertThrows(SecurityException.class,
                () -> adapterWithDefinitions("tests.queue.registered", "tests.queue.other")
                        .resolveServiceTypeForMessage("tests.queue.registered", envelope));

        assertEquals("Queue message serviceType override is not allowed", ex.getMessage());
    }

    @Test
    void queueMessageServiceTypeOverrideRequiresAllowlist() {
        Environment.put("EA_QUEUE_SERVICE_TYPE_OVERRIDE_ALLOWLIST", "tests.queue.other");
        JsonObject envelope = new JsonObject();
        envelope.addProperty("serviceType", "tests.queue.other");

        String serviceType = adapterWithDefinitions("tests.queue.registered", "tests.queue.other")
                .resolveServiceTypeForMessage("tests.queue.registered", envelope);

        assertEquals("tests.queue.other", serviceType);
    }

    @Test
    void swarmControllerQueuePreservesRegisteredDispatchForRunnerEnvelope() {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("serviceType", "pipeline.heartbeat");
        JsonObject params = new JsonObject();

        String serviceType = adapterWithDefinitions("swarm.command.dispatch", "pipeline.heartbeat")
                .resolveServiceTypeForMessage("swarm.command.dispatch",
                        "swarm.controller.commands",
                        envelope,
                        params);

        assertEquals("swarm.command.dispatch", serviceType);
        assertEquals("pipeline.heartbeat", params.get("_queueEnvelopeServiceType").getAsString());
    }

    private ArtemisQueueAdapter adapterWithDefinitions(String... serviceTypes) {
        Engine engine = new Engine();
        for (String serviceType : serviceTypes) {
            ServiceCallDefinition definition = new ServiceCallDefinition();
            definition.setServiceType(serviceType);
            engine.getRegistry().registerDefinition(definition);
        }
        return new ArtemisQueueAdapter(engine);
    }
}
