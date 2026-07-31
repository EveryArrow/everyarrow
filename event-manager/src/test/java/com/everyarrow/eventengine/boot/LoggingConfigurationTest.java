package com.everyarrow.eventengine.boot;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingConfigurationTest {

    @Test
    void writesBelowTheEventManagerComponentDirectory() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("log4j2.xml")) {
            assertNotNull(input);
            String configuration = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(configuration.contains("${env:EA_LOG_ROOT:-logs}"));
            assertTrue(configuration.contains("${logRoot}/event-manager"));
            assertFalse(configuration.contains("${logRoot}/dittoe"));
        }
    }
}
