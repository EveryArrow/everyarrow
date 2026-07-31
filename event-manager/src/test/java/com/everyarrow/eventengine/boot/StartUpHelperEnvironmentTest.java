package com.everyarrow.eventengine.boot;

import com.everyarrow.utilities.environment.Environment;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class StartUpHelperEnvironmentTest {

    @Test
    void usesProcessEnvironmentWhenNoEnvFileIsConfigured() {
        Map.Entry<String, String> processValue = System.getenv().entrySet().stream()
                .filter(entry -> entry.getValue() != null
                        && !entry.getValue().isBlank()
                        && !entry.getValue().contains("\\n"))
                .findFirst()
                .orElseThrow();
        String originalResource = System.getProperty("EA_ENV_RESOURCE");

        try {
            System.clearProperty("EA_ENV_RESOURCE");
            Environment.put("EA_STARTUP_STALE_TEST_VALUE", "stale");

            StartUpHelper.loadEnvironment("", "");

            assertFalse(Environment.getInstance().has("EA_STARTUP_STALE_TEST_VALUE"));
            assertEquals(processValue.getValue(), Environment.getInstance().get(processValue.getKey()));
        } finally {
            if (originalResource == null) {
                System.clearProperty("EA_ENV_RESOURCE");
            } else {
                System.setProperty("EA_ENV_RESOURCE", originalResource);
            }
            Environment.reset();
        }
    }
}
