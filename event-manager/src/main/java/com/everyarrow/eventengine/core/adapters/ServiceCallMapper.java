package com.everyarrow.eventengine.core.adapters;

import com.everyarrow.eventengine.core.services.runtime.PerchRuntimeMetadata;
import com.everyarrow.eventengine.core.services.runtime.ServiceCall;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Locale;
import java.util.Map;

/**
 * Converts any IncomingEvent into a ServiceCall.
 *
 * All adapters (REST, queue, webhook) use this.
 */
public class ServiceCallMapper {

    public static ServiceCall map(IncomingEvent event) {

        ServiceCall call = new ServiceCall();

        // 1. TYPE (required)
       // call.setType(event.getServiceCallType());

        // 2. PARAMS (JSON body)
        String body = event.getBody();
        JsonObject json = null;

        if (body != null && !body.isBlank()) {
            try {
                json = JsonParser.parseString(body).getAsJsonObject();
            } catch (Exception ex) {
                json = new JsonObject();
                json.addProperty("__raw", body);
            }
        } else {
            json = new JsonObject();
        }

        call.setParams(json);

        // 3. TIMESTAMP / METADATA
        Map<String, String> metadata = event.getMetadata();
        call.putMetadata("timestamp", event.getTimestamp());
        call.putMetadata("metadata", metadata);
        PerchRuntimeMetadata.normalizeFromParams(call);
        PerchRuntimeMetadata.normalizeFromQueueAttributes(call, metadata);
        PerchRuntimeMetadata.normalizeFromHttpHeaders(
                call,
                name -> metadata == null ? null : metadata.get("header:" + name.toLowerCase(Locale.ROOT)));

        return call;
    }
}
