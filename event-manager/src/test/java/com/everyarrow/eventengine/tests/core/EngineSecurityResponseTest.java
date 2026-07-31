/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.tests.core;

import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.eventengine.core.hooks.SecurityAccessDeniedException;
import com.everyarrow.eventengine.core.hooks.SecurityAuthenticationException;
import com.everyarrow.eventengine.core.hooks.ServiceHook;
import com.everyarrow.eventengine.core.services.plugins.PluginManager;
import com.everyarrow.eventengine.core.services.runtime.SecurityContext;
import com.everyarrow.eventengine.core.services.runtime.ServiceCall;
import com.everyarrow.eventengine.core.services.runtime.ServiceCallSource;
import com.everyarrow.eventengine.spi.handler.ServiceCallHandler;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.everyarrow.eventengine.spi.model.ServiceCallResponse;
import com.everyarrow.utilities.environment.Environment;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EngineSecurityResponseTest {

    @AfterEach
    void resetEnvironment() {
        Environment.reset();
    }

    @Test
    void securityAccessDeniedMapsToForbiddenResponse() throws Exception {
        Engine engine = new Engine();
        ServiceCallDefinition def = new ServiceCallDefinition();
        def.setServiceType("tests.secured.echo");
        def.setSource("core:test");
        engine.getRegistry().register("tests.secured.echo", new EchoHandler(), def);
        engine.getHookManager().register(new DenyHook());

        ServiceCall call = new ServiceCall();
        call.setType("tests.secured.echo");

        ServiceCallResponse response = engine.execute(call);

        assertEquals(403, response.getHttpStatus());
        assertEquals("Access denied", response.getBodyAsObject().get("error").getAsString());
    }

    @Test
    void securityAuthenticationFailureMapsToUnauthorizedResponse() throws Exception {
        Engine engine = new Engine();
        ServiceCallDefinition def = new ServiceCallDefinition();
        def.setServiceType("tests.secured.echo");
        def.setSource("core:test");
        engine.getRegistry().register("tests.secured.echo", new EchoHandler(), def);
        engine.getHookManager().register(new RejectAuthenticationHook());

        ServiceCall call = new ServiceCall();
        call.setType("tests.secured.echo");

        ServiceCallResponse response = engine.execute(call);

        assertEquals(401, response.getHttpStatus());
        assertEquals("Authentication failed", response.getBodyAsObject().get("error").getAsString());
    }

    @Test
    void serviceCallExecuteResolvesMethodInsidePluginRegistryFolder() throws Exception {
        Path pluginsRoot = Files.createTempDirectory("ea-service-call-folder-execute");
        writeSchemaRegistry(
                pluginsRoot.resolve("marketing"),
                "marketing-public.json",
                "marketing",
                "public",
                "create_prospect",
                "marketing.public.create_prospect");
        writeSchemaRegistry(
                pluginsRoot.resolve("perch"),
                "schema_registry.json",
                "EveryArrow",
                "perch",
                "create_prospect",
                "perch.telemetry.create_prospect");

        CapturingEngine engine = new CapturingEngine(pluginsRoot);
        ServiceCall parent = new ServiceCall(engine);

        JsonObject params = new JsonObject();
        JsonElement result = parent.execute("marketing", "create_prospect", params);

        assertEquals("marketing.public.create_prospect", engine.lastType);
        assertEquals("marketing.public.create_prospect", result.getAsJsonObject().get("type").getAsString());
    }

    @Test
    void securedExternalCallFailsClosedWhenSecurityPluginIsAbsent() throws Exception {
        Environment.put("TESTS_CONSOLE_ID", "tests-app-123");

        Engine engine = new Engine();
        ServiceCallDefinition def = new ServiceCallDefinition();
        def.setServiceType("tests.secured.echo");
        def.setSource("core:test");
        def.annotations = new ServiceCallDefinition.Annotations();
        def.annotations.call = new ServiceCallDefinition.CallMeta();
        def.annotations.call.applicationKey = "TESTS";
        engine.getRegistry().register("tests.secured.echo", new EchoHandler(), def);

        ServiceCall call = new ServiceCall();
        call.setType("tests.secured.echo");
        call.setSource(ServiceCallSource.REST);
        JsonObject params = new JsonObject();
        params.addProperty("_api_key", "raw-api-key");
        call.setParams(params);

        ServiceCallResponse response = engine.execute(call);

        assertEquals(401, response.getHttpStatus());
        assertEquals("Authentication failed", response.getBodyAsObject().get("error").getAsString());
    }

    @Test
    void handlerFailureMapsToServerErrorWithoutRawException() throws Exception {
        Engine engine = new Engine();
        ServiceCallDefinition def = new ServiceCallDefinition();
        def.setServiceType("tests.secured.fail");
        def.setSource("core:test");
        engine.getRegistry().register("tests.secured.fail", new FailingHandler(), def);

        ServiceCall call = new ServiceCall();
        call.setType("tests.secured.fail");

        ServiceCallResponse response = engine.execute(call);

        assertEquals(500, response.getHttpStatus());
        assertEquals("Internal server error", response.getBodyAsObject().get("error").getAsString());
    }

    @Test
    void explicitDatabaseErrorBodyMapsToServerError() throws Exception {
        Engine engine = new Engine();
        ServiceCallDefinition def = new ServiceCallDefinition();
        def.setServiceType("tests.database.error");
        def.setSource("core:test");
        engine.getRegistry().register("tests.database.error",
                (params, type, source) -> {
                    JsonObject body = new JsonObject();
                    body.addProperty("status", "error");
                    body.addProperty("message", "constraint violation");
                    return body;
                },
                def);

        ServiceCall call = new ServiceCall();
        call.setType("tests.database.error");

        ServiceCallResponse response = engine.execute(call);

        assertEquals(500, response.getHttpStatus());
        assertEquals("error", response.getBodyAsObject().get("status").getAsString());
    }

    @Test
    void openExternalCallRunsWhenSecurityPluginIsAbsent() throws Exception {
        Engine engine = new Engine();
        ServiceCallDefinition def = new ServiceCallDefinition();
        def.setServiceType("tests.open.echo");
        def.setSource("core:test");
        def.annotations = new ServiceCallDefinition.Annotations();
        def.annotations.call = new ServiceCallDefinition.CallMeta();
        def.annotations.call.open = true;
        engine.getRegistry().register("tests.open.echo", new EchoHandler(), def);

        ServiceCall call = new ServiceCall();
        call.setType("tests.open.echo");
        call.setSource(ServiceCallSource.REST);
        call.setParams(new JsonObject());

        ServiceCallResponse response = engine.execute(call);

        assertEquals(200, response.getHttpStatus());
        assertEquals(true, response.getBodyAsObject().get("ok").getAsBoolean());
    }

    @Test
    void legacyHandlerReceivesRuntimeSourceName() throws Exception {
        Engine engine = new Engine();
        ServiceCallDefinition def = publicDefinition("tests.source.echo");
        engine.getRegistry().register("tests.source.echo", new SourceEchoHandler(), def);

        ServiceCall call = new ServiceCall();
        call.setType("tests.source.echo");
        call.setSource(ServiceCallSource.REST);
        call.setParams(new JsonObject());

        ServiceCallResponse response = engine.execute(call);

        assertEquals(200, response.getHttpStatus());
        assertEquals("REST", response.getBodyAsObject().get("source").getAsString());
    }

    @Test
    void typedHandlerReceivesFullRuntimeCall() throws Exception {
        Engine engine = new Engine();
        ServiceCallDefinition def = publicDefinition("tests.typed.echo");
        engine.getRegistry().register("tests.typed.echo", new TypedEchoHandler(), def);

        ServiceCall call = new ServiceCall();
        call.setType("tests.typed.echo");
        call.setSource(ServiceCallSource.REST);
        call.setParams(new JsonObject());
        call.putMetadata("trace.id", "trace-123");

        ServiceCallResponse response = engine.execute(call);

        assertEquals(200, response.getHttpStatus());
        JsonObject body = response.getBodyAsObject();
        assertEquals("REST", body.get("source").getAsString());
        assertEquals("trace-123", body.get("trace_id").getAsString());
        assertEquals(true, body.get("has_security_context").getAsBoolean());
    }

    private void writeSchemaRegistry(
            Path pluginDir,
            String fileName,
            String database,
            String schema,
            String function,
            String serviceType) throws Exception {
        Files.createDirectories(pluginDir);
        String routePath = "/" + serviceType.replace('.', '-');
        Files.writeString(pluginDir.resolve(fileName), """
                {
                  "schema": {
                    "database": "%s",
                    "name": "%s"
                  },
                  "services": [
                    {
                      "database": "%s",
                      "schema": "%s",
                      "function": "%s",
                      "serviceType": "%s",
                      "httpMethod": "POST",
                      "routePath": "%s",
                      "annotations": {
                        "call": {
                          "applicationKey": "TESTS"
                        },
                        "secured": {
                          "roles": ["system"],
                          "scopes": ["tests:execute"]
                        }
                      }
                    }
                  ]
                }
                """.formatted(database, schema, database, schema, function, serviceType, routePath),
                StandardCharsets.UTF_8);
    }

    private static final class CapturingEngine extends Engine {
        private final PluginManager pluginManager;
        private String lastType;

        private CapturingEngine(Path pluginsRoot) {
            pluginManager = new PluginManager(getRegistry(), pluginsRoot.toFile());
            pluginManager.setEngine(this);
            pluginManager.loadPlugins();
        }

        @Override
        public PluginManager getPluginManager() {
            return pluginManager;
        }

        @Override
        public ServiceCallResponse execute(ServiceCall call) {
            lastType = call.getType();

            JsonObject result = new JsonObject();
            result.addProperty("ok", true);
            result.addProperty("type", lastType);
            return ServiceCallResponse.ok(lastType, result, 0);
        }
    }

    private static final class EchoHandler implements ServiceCallHandler {

        @Override
        public JsonObject execute(JsonObject params, String type, String source) {
            JsonObject result = new JsonObject();
            result.addProperty("ok", true);
            return result;
        }
    }

    private static final class SourceEchoHandler implements ServiceCallHandler {

        @Override
        public JsonObject execute(JsonObject params, String type, String source) {
            JsonObject result = new JsonObject();
            result.addProperty("source", source);
            return result;
        }
    }

    private static final class TypedEchoHandler implements ServiceCallHandler {

        @Override
        public JsonElement execute(ServiceCall call) {
            JsonObject result = new JsonObject();
            result.addProperty("source", call.getSource().name());
            result.addProperty("trace_id", String.valueOf(call.getMetadata("trace.id")));
            result.addProperty("has_security_context", call.getSecurityContext() instanceof SecurityContext);
            return result;
        }

        @Override
        public JsonObject execute(JsonObject params, String type, String source) {
            throw new AssertionError("typed handler should receive the full ServiceCall");
        }
    }

    private static final class FailingHandler implements ServiceCallHandler {

        @Override
        public JsonObject execute(JsonObject params, String type, String source) {
            throw new IllegalStateException("internal handler detail");
        }
    }

    private static final class DenyHook implements ServiceHook {

        @Override
        public void before(ServiceCall call) {
            throw new SecurityAccessDeniedException("blocked");
        }

        @Override
        public void after(ServiceCall call, Object result) {
        }
    }

    private static final class RejectAuthenticationHook implements ServiceHook {

        @Override
        public void before(ServiceCall call) {
            throw new SecurityAuthenticationException("bad-token");
        }

        @Override
        public void after(ServiceCall call, Object result) {
        }
    }

    private ServiceCallDefinition publicDefinition(String serviceType) {
        ServiceCallDefinition def = new ServiceCallDefinition();
        def.setServiceType(serviceType);
        def.setSource("core:test");
        def.annotations = new ServiceCallDefinition.Annotations();
        def.annotations.secured = new ServiceCallDefinition.SecuredMeta();
        def.annotations.secured.isPublic = true;
        return def;
    }
}
