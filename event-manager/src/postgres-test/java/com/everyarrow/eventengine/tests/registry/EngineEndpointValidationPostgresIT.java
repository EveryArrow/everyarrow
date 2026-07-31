package com.everyarrow.eventengine.tests.registry;

import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.eventengine.core.services.runtime.ServiceCall;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.everyarrow.eventengine.spi.model.ServiceCallResponse;
import com.everyarrow.utilities.environment.Environment;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Validates all DB endpoints registered in the service_registry_db.json
 * by routing them through the Engine's execution pipeline.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class EngineEndpointValidationPostgresIT {

    private Engine engine;

    @BeforeAll
    void setupEngine() throws Exception {
        String envResource = System.getProperty("EA_ENV_RESOURCE", "ea.env");
        Environment.initFromResource(envResource);

        String overlayResources = System.getProperty("EA_ENV_OVERLAY_RESOURCE", "").trim();
        if (!overlayResources.isEmpty()) {
            for (String overlayResource : overlayResources.split(",")) {
                String trimmedOverlay = overlayResource.trim();
                if (!trimmedOverlay.isEmpty()) {
                    Environment.loadComponent(trimmedOverlay);
                }
            }
        }

        engine = new Engine();
        engine.initialize();
        System.out.println("Engine initialized for validation test.");
    }

    @AfterAll
    void teardownEngine() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void validateAllDbEndpoints() {
        Collection<ServiceCallDefinition> defs = engine.getRegistry().listDefinitions();
        List<ServiceCallDefinition> databaseDefinitions = defs.stream()
                .filter(definition -> "Database".equals(definition.getCategory()))
                .toList();
        assertFalse(databaseDefinitions.isEmpty(),
                "PostgreSQL endpoint validation requires database registry definitions");

        System.out.println("\n--- Starting DB Endpoint Validation (" + defs.size() + " total) ---");

        int passed = 0;
        int failed = 0;

        // Group by result
        Map<String, String> failures = new TreeMap<>();

        String logFilePath = Paths.get(System.getProperty("user.dir"), "target", "validation_raw_logs.txt").toString();

        try (PrintWriter logWriter = new PrintWriter(new FileWriter(logFilePath))) {
            logWriter.println("===== RAW ENDPOINT VALIDATION LOGS =====");

            for (ServiceCallDefinition def : databaseDefinitions) {
                String type = def.getServiceType();

                ServiceCall call = new ServiceCall();
                call.setType(type);
                call.setParams(new JsonObject()); // empty json object as payload

                try {
                    ServiceCallResponse res = engine.execute(call);

                    // If it returns a standard JSON object with an "error" property, check it
                    if (res != null && res.getBody() != null && res.getBody().isJsonObject()
                            && res.getBody().getAsJsonObject().has("error")) {
                        String err = res.getBody().getAsJsonObject().get("error").getAsString();

                        logWriter.println(
                                "--------------------------------------------------------------------------------");
                        logWriter.println(">>> TESTING: " + type);
                        logWriter.println("    [FAILURE] Schema or Execution Error:");
                        logWriter.println("    " + err.replace("\n", "\n    "));
                        logWriter.println(
                                "--------------------------------------------------------------------------------\n");

                        // We consider "function does not exist" or "permission denied" as strict
                        // FAILURES
                        if (err.contains("function") && err.contains("does not exist")) {
                            failures.put(type, "Missing Function: " + err);
                            failed++;
                        } else if (err.contains("permission denied")) {
                            failures.put(type, "Permission Denied: " + err);
                            failed++;
                        } else {
                            // Expected validation error
                            passed++;
                            failures.put(type, "Validation Error: " + err);
                        }
                    } else {
                        logWriter.println(
                                "--------------------------------------------------------------------------------");
                        logWriter.println(">>> TESTING: " + type);
                        logWriter.println("    [SUCCESS] Executed without unhandled DB exception.");
                        if (res != null && res.getBody() != null) {
                            String bodyStr = res.getBody().toString();
                            logWriter.println("    " + bodyStr.substring(0, Math.min(bodyStr.length(), 300)));
                        }
                        logWriter.println(
                                "--------------------------------------------------------------------------------\n");
                        passed++;
                    }
                } catch (Exception e) {
                    // If the engine throws completely outside of the normal flow
                    logWriter.println(
                            "--------------------------------------------------------------------------------");
                    logWriter.println(">>> TESTING: " + type);
                    logWriter.println("    [CRASH] Fatal Java Exception:");
                    e.printStackTrace(logWriter);
                    logWriter.println(
                            "--------------------------------------------------------------------------------\n");

                    failures.put(type, "Unexpected Exception: " + e.getMessage());
                    failed++;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("--- Validation Summary ---");
        System.out.println("Passed (Valid or Expected Validation Err): " + passed);
        System.out.println("Failed (Missing Function or Permission)  : " + failed);

        System.out.println("\n# Validation Schema Errors\n");
        System.out.println("| Endpoint | Error |\n|---|---|");
        failures.forEach((k, v) -> {
            String cleanErr = v.replace("\n", " ").replace("|", "\\|");
            System.out.println("| `" + k + "` | ```" + cleanErr + "``` |");
        });
        assertEquals(0, failed, "Database endpoint validation found failures");
    }
}
