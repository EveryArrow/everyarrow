// src/main/java/com/everyarrow/eventengine/core/services/tests/CalendarJobTestService.java
package com.everyarrow.eventengine.tests.eventtype;

import com.everyarrow.eventengine.core.services.runtime.ServiceCall;
import com.everyarrow.eventengine.spi.annotations.CalendarJob;
import com.everyarrow.eventengine.spi.handler.ServiceCallHandler;
import com.google.gson.JsonObject;

public class CalendarJobTestService implements ServiceCallHandler {

    private static volatile boolean didRun = false;

    public static void reset() {
        didRun = false;
    }

    public static boolean didRun() {
        return didRun;
    }

    @Override
    @CalendarJob(
            cron = "0/2 * * * * ?",   // every 2 seconds
            timezone = "UTC"
    )
    public JsonObject execute(JsonObject params, String type, String source) {
        System.out.println("CalendarJobTestService.execute() fired");
        didRun = true;

        JsonObject result = new JsonObject();
        result.addProperty("status", "ok");
        return result;
    }

    @Override
    public JsonObject onError(ServiceCall call, Exception ex) {
        JsonObject error = new JsonObject();
        error.addProperty("error", ex == null ? "unknown" : ex.getMessage());
        return error;
    }
}
