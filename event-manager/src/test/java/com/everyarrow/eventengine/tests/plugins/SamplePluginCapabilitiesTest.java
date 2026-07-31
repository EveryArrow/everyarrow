/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.tests.plugins;

import com.everyarrow.eventengine.core.services.plugins.PluginManager;
import com.everyarrow.eventengine.core.services.registry.ServiceRegistry;
import com.everyarrow.eventengine.spi.handler.ServiceCallHandler;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class SamplePluginCapabilitiesTest {

    private static final String SAMPLE_REST_HANDLER =
            "com.everyarrow.eventengine.plugins.sample.TestRestHandler";
    private static final String SAMPLE_WEBHOOK_HANDLER =
            "com.everyarrow.eventengine.plugins.sample.TestWebhookHandler";
    private static final String SAMPLE_CALENDAR_HANDLER =
            "com.everyarrow.eventengine.plugins.sample.TestCalendarHandler";
    private static final String SAMPLE_QUEUE_HANDLER =
            "com.everyarrow.eventengine.plugins.sample.TestQueueConsumerHandler";

    @Test
    void samplePluginShouldRegisterRestWebhookCalendarAndQueueConsumerDefinitions() throws Exception {
        Path sampleJar = SamplePluginJarFixture.build();
        assertTrue(Files.exists(sampleJar), "Expected sample plugin jar to exist: " + sampleJar.toAbsolutePath());

        Path tempPluginsRoot = createPluginRootWithNestedSampleJar(sampleJar, "sample");

        ServiceRegistry registry = new ServiceRegistry();
        PluginManager pluginManager = new PluginManager(registry, tempPluginsRoot.toFile());

        pluginManager.loadPlugins();

        assertFalse(pluginManager.getLoadedPluginNames().isEmpty(),
                "Expected at least one loaded plugin");

        assertTrue(pluginManager.findClass(SAMPLE_REST_HANDLER).isPresent(),
                "Expected TestRestHandler to be loaded");
        assertTrue(pluginManager.findClass(SAMPLE_WEBHOOK_HANDLER).isPresent(),
                "Expected TestWebhookHandler to be loaded");
        assertTrue(pluginManager.findClass(SAMPLE_CALENDAR_HANDLER).isPresent(),
                "Expected TestCalendarHandler to be loaded");
        assertTrue(pluginManager.findClass(SAMPLE_QUEUE_HANDLER).isPresent(),
                "Expected TestQueueConsumerHandler to be loaded");

        Optional<ServiceCallDefinition> restDef = pluginManager.getDefinition("testRest");
        assertTrue(restDef.isPresent(), "Expected testRest definition");
        assertTrue(restDef.get().hasRestRoute(), "Expected REST metadata");
        assertEquals("/sample/rest", restDef.get().getRestRoute().path);
        assertEquals("GET", restDef.get().getRestRoute().method);

        Optional<ServiceCallDefinition> webhookDef = pluginManager.getDefinition("testWebhook");
        assertTrue(webhookDef.isPresent(), "Expected testWebhook definition");
        assertTrue(webhookDef.get().hasWebhook(), "Expected webhook metadata");
        assertEquals("/sample/webhook", webhookDef.get().getWebhook().path);

        Optional<ServiceCallDefinition> calendarDef = pluginManager.getDefinition("testCalendar");
        assertTrue(calendarDef.isPresent(), "Expected testCalendar definition");
        assertTrue(calendarDef.get().hasCalendarJobs(), "Expected calendar metadata");
        assertEquals(1, calendarDef.get().getCalendarJobs().size());
        assertEquals("sampleCalendarJob", calendarDef.get().getCalendarJobs().get(0).id);
        assertEquals("UTC", calendarDef.get().getCalendarJobs().get(0).timezone);
        assertEquals("runScheduledJob", calendarDef.get().getCalendarJobs().get(0).methodName);

        Optional<ServiceCallDefinition> queueDef = pluginManager.getDefinition("testQueueConsumer");
        assertTrue(queueDef.isPresent(), "Expected testQueueConsumer definition");
        assertTrue(queueDef.get().hasQueueSubscriptions(), "Expected queue subscription metadata");
        assertEquals(1, queueDef.get().getQueueSubscriptions().size());
        assertEquals("sample.queue.consumer", queueDef.get().getQueueSubscriptions().get(0).destination);
        assertEquals(3, queueDef.get().getQueueSubscriptions().get(0).maxRetries);
        assertEquals("messageType = 'sample'", queueDef.get().getQueueSubscriptions().get(0).selector);
        assertEquals("execute", queueDef.get().getQueueSubscriptions().get(0).methodName);
    }

    @Test
    void samplePluginShouldLoadWhenJarIsNestedRecursively() throws Exception {
        Path sourceJar = SamplePluginJarFixture.build();
        Path tempPluginsRoot = createPluginRootWithNestedSampleJar(sourceJar, "sample");

        ServiceRegistry registry = new ServiceRegistry();
        PluginManager pluginManager = new PluginManager(registry, tempPluginsRoot.toFile());
        pluginManager.loadPlugins();

        assertTrue(pluginManager.findClass(SAMPLE_REST_HANDLER).isPresent(),
                "Expected TestRestHandler to be loaded from nested plugin JAR");
        assertTrue(pluginManager.getDefinition("testRest").isPresent(),
                "Expected testRest definition from nested plugin JAR");
    }

    @Test
    void manifestHandlerShouldMergeQueueAnnotationWithoutRegisteringDuplicateCandidate() throws Exception {
        Path sourceJar = SamplePluginJarFixture.build();
        Path tempPluginsRoot = createPluginRootWithNestedSampleJar(sourceJar, "sample");
        Path pluginDir = tempPluginsRoot.resolve("sample");
        Files.writeString(pluginDir.resolve("plugin.json"), """
                {
                  "plugin": {
                    "id": "sample",
                    "version": "1.0.0"
                  },
                  "services": [
                    {
                      "serviceType": "testQueueConsumer",
                      "handlerClass": "%s",
                      "source": "plugin:sample",
                      "category": "Sample"
                    }
                  ]
                }
                """.formatted(SAMPLE_QUEUE_HANDLER), StandardCharsets.UTF_8);

        ServiceRegistry registry = new ServiceRegistry();
        PluginManager pluginManager = new PluginManager(registry, tempPluginsRoot.toFile());

        assertDoesNotThrow(pluginManager::loadPlugins);

        ServiceCallDefinition definition = registry.getDefinition("testQueueConsumer");
        assertNotNull(definition);
        assertTrue(definition.hasQueueSubscriptions());
        assertEquals(1, definition.getQueueSubscriptions().size());
        assertEquals("sample.queue.consumer", definition.getQueueSubscriptions().get(0).destination);
        assertEquals(1, registry.getDefinitionsFor("testQueueConsumer").size());

        ServiceCallHandler handler = (ServiceCallHandler) pluginManager.findClass(SAMPLE_QUEUE_HANDLER)
                .orElseThrow()
                .getDeclaredConstructor()
                .newInstance();
        assertDoesNotThrow(() -> registry.register("testQueueConsumer", handler, definition));
        assertSame(handler, registry.get("testQueueConsumer"));
        assertEquals(1, registry.getDefinitionsFor("testQueueConsumer").size());
    }

    private Path createPluginRootWithNestedSampleJar(Path sourceJar, String pluginName) throws Exception {
        Path tempPluginsRoot = Files.createTempDirectory("ea-plugin-recursive-test");
        Path nestedLibDir = tempPluginsRoot.resolve(pluginName).resolve("lib");
        Files.createDirectories(nestedLibDir);

        Path nestedJar = nestedLibDir.resolve(sourceJar.getFileName());
        Files.copy(sourceJar, nestedJar, StandardCopyOption.REPLACE_EXISTING);

        return tempPluginsRoot;
    }

}
