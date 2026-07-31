package com.everyarrow.eventengine.tests.eventtype;

import com.everyarrow.eventengine.spi.annotations.Webhook;
import com.everyarrow.eventengine.spi.handler.ServiceCallHandler;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

/**
 * Demo webhook handler that:
 *   - Extracts "csv" field from JSON payload
 *   - Parses it as CSV text
 *   - Prints each row to the console.
 */
public class DemoWebhookCsvHandler implements ServiceCallHandler {

    @Override
    @Webhook(path = "/webhooks/csv-demo")
    public JsonObject execute(JsonObject params, String serviceType, String source) throws Exception {

        JsonElement csvElement = params.get("csv");
        if (csvElement == null || csvElement.isJsonNull()) {
            JsonObject error = new JsonObject();
            error.addProperty("error", "Missing 'csv' field in webhook payload.");
            return error;
        }

        String csvText = csvElement.getAsString();

        try (BufferedReader reader = new BufferedReader(new StringReader(csvText))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[Webhook CSV Demo] Row: " + line);
            }
        } catch (IOException io) {
            throw new RuntimeException("Error reading CSV payload", io);
        }

        JsonObject reply = new JsonObject();
        reply.addProperty("status", "ok");
        reply.addProperty("serviceType", serviceType);
        reply.addProperty("source", source);
        reply.addProperty("message", "CSV processed and printed to console.");

        return reply;
    }
}
