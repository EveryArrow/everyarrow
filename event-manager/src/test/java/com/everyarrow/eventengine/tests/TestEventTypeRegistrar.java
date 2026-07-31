// src/test/java/com/everyarrow/eventengine/tests/TestEventTypeRegistrar.java
package com.everyarrow.eventengine.tests;

import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.eventengine.core.adapters.AnnotationDefinitionBuilder;
import com.everyarrow.eventengine.spi.handler.ServiceCallHandler;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.everyarrow.eventengine.tests.eventtype.CalendarJobTestService;
import com.everyarrow.eventengine.tests.eventtype.DemoWebhookCsvHandler;
import com.everyarrow.eventengine.tests.eventtype.RestTest;
import com.everyarrow.eventengine.tests.eventtype.QueueHelloWorldService;
import com.everyarrow.eventengine.tests.eventtype.WebSocketEchoHandler;

public final class TestEventTypeRegistrar {
    private TestEventTypeRegistrar() {}

    public static void registerAll(Engine engine) {
        register(engine, new RestTest(engine), RestTest.class);
        register(engine, new DemoWebhookCsvHandler(), DemoWebhookCsvHandler.class);
        register(engine, new CalendarJobTestService(), CalendarJobTestService.class);
        register(engine, new QueueHelloWorldService(), QueueHelloWorldService.class);
        register(engine, new WebSocketEchoHandler(), WebSocketEchoHandler.class);
    }

    private static void register(Engine engine, ServiceCallHandler handler, Class<?> clazz) {
        ServiceCallDefinition def = AnnotationDefinitionBuilder.buildDefinition(clazz);
        if (def == null) throw new IllegalStateException("No definition for " + clazz.getName());
        engine.getRegistry().register(def.getServiceType(), handler, def);

        System.out.println("[TestEventTypeRegistrar] Registered serviceType=" + def.getServiceType()
                + " class=" + clazz.getName()
                + " rest=" + def.hasRestRoute()
                + " webhook=" + def.hasWebhook()
                + " websocket=" + def.hasWebSocketRoute()
                + " calendar=" + def.hasCalendarJobs()
                + " queue=" + def.hasQueueSubscriptions()
        );
    }
}
