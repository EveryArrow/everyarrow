package com.everyarrow.eventengine.plugins.sample;


import com.everyarrow.eventengine.spi.annotations.CalendarJob;
import com.everyarrow.eventengine.spi.annotations.ServiceCallName;
import com.everyarrow.eventengine.spi.handler.ServiceCallHandler;
import com.google.gson.JsonObject;

public class TestCalendarHandler implements ServiceCallHandler {

    @Override
    public JsonObject execute(JsonObject params, String serviceType, String source) {
        JsonObject out = new JsonObject();
        out.addProperty("status", "ok");
        out.addProperty("handler", "sampleCalendar");
        return out;
    }

    @CalendarJob(id = "sampleCalendarJob", cron = "0 0/5 * * * ?", timezone = "UTC")
    public void runScheduledJob() {
        // metadata-only test hook
    }
}
