package com.everyarrow.eventengine.boot;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmbeddedArtemisTcpBrokerTest {

    @Test
    void loopbackBrokerBindsLoopbackByDefault() {
        EmbeddedArtemisTcpBroker.BrokerPlan plan =
                EmbeddedArtemisTcpBroker.plan("tcp://localhost:61616", "", "", false);

        assertEquals("127.0.0.1", plan.bindHost());
        assertEquals(61616, plan.port());
        assertEquals(false, plan.securityEnabled());
    }

    @Test
    void nonLoopbackBrokerDoesNotStartWithoutExplicitOptIn() {
        EmbeddedArtemisTcpBroker.BrokerPlan plan =
                EmbeddedArtemisTcpBroker.plan("tcp://0.0.0.0:61616", "user", "pass", false);

        assertNull(plan);
    }

    @Test
    void publicBrokerOptInRequiresCredentials() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> EmbeddedArtemisTcpBroker.plan("tcp://0.0.0.0:61616", "", "", true));

        assertEquals("Embedded Artemis public TCP binding requires JMS_USERNAME and JMS_PASSWORD", ex.getMessage());
    }

    @Test
    void publicBrokerOptInUsesRequestedBindWithCredentials() {
        EmbeddedArtemisTcpBroker.BrokerPlan plan =
                EmbeddedArtemisTcpBroker.plan("tcp://0.0.0.0:61616", "user", "pass", true);

        assertEquals("0.0.0.0", plan.bindHost());
        assertEquals(true, plan.securityEnabled());
    }

    @Test
    void runtimeDirectoryDefaultsUnderWorkingDirectory() {
        Path expected = Path.of(System.getProperty("user.dir"), ".ea-artemis", "ea-artemis-61616");

        assertEquals(expected, EmbeddedArtemisTcpBroker.runtimeDirectory(61616));
    }
}
