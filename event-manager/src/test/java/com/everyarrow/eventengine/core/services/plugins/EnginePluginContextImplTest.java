package com.everyarrow.eventengine.core.services.plugins;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnginePluginContextImplTest {

    @Test
    void getUsesJvmSystemPropertyBeforeEnvironment() {
        String key = "EA_TEST_PLUGIN_CONTEXT_VALUE";
        String original = System.getProperty(key);
        try {
            System.setProperty(key, "from-property");
            EnginePluginContextImpl context = new EnginePluginContextImpl();

            assertEquals("from-property", context.get(key));
            assertEquals("from-property", context.get(key, "fallback"));
        } finally {
            if (original == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, original);
            }
        }
    }
}
