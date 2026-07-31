package com.everyarrow.eventengine.core.services.registry;

import com.everyarrow.eventengine.core.services.metadata.RegistryListHandler;
import com.everyarrow.eventengine.core.services.runtime.GenericProcessHandler;
import com.everyarrow.eventengine.spi.handler.ServiceCallHandler;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceRegistryTest {

    private static final String SERVICE_TYPE =
            "security.access_control.api_key_create";

    @Test
    void localAnnotationWinsWhenServerFunctionIsRegisteredFirst() {
        ServiceRegistry registry = new ServiceRegistry();
        TestSecurityHandler localHandler = new TestSecurityHandler();
        ServiceCallDefinition local = localDefinition(
                SERVICE_TYPE,
                TestSecurityHandler.class,
                "plugin:local");

        registerServerFunction(registry, dbDefinition(SERVICE_TYPE, "api_key_create"));
        registry.register(SERVICE_TYPE, localHandler, local);

        assertSelectedLocalHandler(registry, local, localHandler);
    }

    @Test
    void localAnnotationWinsWhenServerFunctionIsRegisteredLast() {
        ServiceRegistry registry = new ServiceRegistry();
        TestSecurityHandler localHandler = new TestSecurityHandler();
        ServiceCallDefinition local = localDefinition(
                SERVICE_TYPE,
                TestSecurityHandler.class,
                "plugin:local");

        registry.register(SERVICE_TYPE, localHandler, local);
        registerServerFunction(registry, dbDefinition(SERVICE_TYPE, "api_key_create"));

        assertSelectedLocalHandler(registry, local, localHandler);
    }

    @Test
    void randomizedRegistrationOrderAlwaysProducesSameResolution() {
        for (int seed = 0; seed < 100; seed++) {
            ServiceRegistry registry = new ServiceRegistry();
            TestSecurityHandler localHandler = new TestSecurityHandler();
            ServiceCallDefinition local = localDefinition(
                    SERVICE_TYPE,
                    TestSecurityHandler.class,
                    "plugin:local");
            ServiceCallDefinition database = dbDefinition(
                    SERVICE_TYPE,
                    "api_key_create");

            List<Consumer<ServiceRegistry>> registrations = new ArrayList<>();
            registrations.add(target -> target.register(
                    SERVICE_TYPE,
                    localHandler,
                    local));
            registrations.add(target -> registerServerFunction(target, database));
            Collections.shuffle(registrations, new Random(seed));
            registrations.forEach(registration -> registration.accept(registry));

            assertSelectedLocalHandler(registry, local, localHandler);
        }
    }

    @Test
    void removingLocalPluginRestoresServerFunctionFallback() {
        ServiceRegistry registry = new ServiceRegistry();
        ServiceCallDefinition local = localDefinition(
                SERVICE_TYPE,
                TestSecurityHandler.class,
                "plugin:local");
        ServiceCallDefinition database = dbDefinition(
                SERVICE_TYPE,
                "api_key_create");

        registry.register(
                SERVICE_TYPE,
                new TestSecurityHandler(),
                local);
        registerServerFunction(registry, database);

        registry.deregisterPlugin("local");

        assertSame(database, registry.getDefinition(SERVICE_TYPE));
        assertInstanceOf(GenericProcessHandler.class, registry.get(SERVICE_TYPE));
        assertEquals("only registered candidate",
                registry.getResolution(SERVICE_TYPE).reason());
    }

    @Test
    void rejectsDifferentLocalHandlersAtTheSamePrecedence() {
        ServiceRegistry registry = new ServiceRegistry();
        ServiceCallDefinition first = localDefinition(
                SERVICE_TYPE,
                TestSecurityHandler.class,
                "plugin:first");
        ServiceCallDefinition second = localDefinition(
                SERVICE_TYPE,
                AlternateSecurityHandler.class,
                "plugin:second");
        registry.register(SERVICE_TYPE, new TestSecurityHandler(), first);

        AmbiguousFunctionRegistrationException error = assertThrows(
                AmbiguousFunctionRegistrationException.class,
                () -> registry.register(
                        SERVICE_TYPE,
                        new AlternateSecurityHandler(),
                        second));

        assertTrue(error.getMessage().contains(SERVICE_TYPE));
        assertTrue(error.getMessage().contains("plugin:first"));
        assertTrue(error.getMessage().contains("plugin:second"));
        assertSame(first, registry.getDefinition(SERVICE_TYPE));
    }

    @Test
    void rejectsDifferentDbFunctionsEvenWhenLocalHandlerWouldWin() {
        ServiceRegistry registry = new ServiceRegistry();
        registry.register(
                SERVICE_TYPE,
                new TestSecurityHandler(),
                localDefinition(
                        SERVICE_TYPE,
                        TestSecurityHandler.class,
                        "plugin:local"));
        registerServerFunction(
                registry,
                dbDefinition(SERVICE_TYPE, "api_key_create"));

        assertThrows(
                AmbiguousFunctionRegistrationException.class,
                () -> registry.register(
                        SERVICE_TYPE,
                        new GenericProcessHandler(),
                        dbDefinition(SERVICE_TYPE, "api_key_create_v2"),
                        FunctionRegistrationOrigin.SERVER_REGISTRY));
    }

    @Test
    void identicalDeclarationIsIdempotent() {
        ServiceRegistry registry = new ServiceRegistry();
        ServiceCallDefinition definition = localDefinition(
                SERVICE_TYPE,
                TestSecurityHandler.class,
                "plugin:local");
        TestSecurityHandler handler = new TestSecurityHandler();

        registry.register(SERVICE_TYPE, handler, definition);
        registry.register(SERVICE_TYPE, handler, definition);

        assertEquals(1, registry.getResolution(SERVICE_TYPE).candidates().size());
        assertEquals(1, registry.getDefinitionsFor(SERVICE_TYPE).size());
    }

    @Test
    void databaseRegistryProjectionRefreshesLocallyDiscoveredFunction() {
        ServiceRegistry registry = new ServiceRegistry();
        ServiceCallDefinition discovered = dbDefinition(
                "connector.customer_server_delete",
                "customer_server_delete");
        ServiceCallDefinition reconciled = dbDefinition(
                "connector.customer_server_delete",
                "customer_server_delete");

        registry.register(
                discovered.getServiceType(),
                new GenericProcessHandler(),
                discovered,
                FunctionRegistrationOrigin.LOCAL_REGISTRY);
        registry.register(
                reconciled.getServiceType(),
                new GenericProcessHandler(),
                reconciled,
                FunctionRegistrationOrigin.SERVER_REGISTRY);

        assertSame(reconciled, registry.getDefinition(reconciled.getServiceType()));
        assertEquals(1, registry.getResolution(reconciled.getServiceType()).candidates().size());
    }

    @Test
    void reservedCoreImplementationCannotBeOverridden() {
        ServiceRegistry registry = new ServiceRegistry();
        ServiceCallDefinition core = localDefinition(
                SERVICE_TYPE,
                TestSecurityHandler.class,
                "core");
        ServiceCallDefinition plugin = localDefinition(
                SERVICE_TYPE,
                AlternateSecurityHandler.class,
                "plugin:local");
        TestSecurityHandler coreHandler = new TestSecurityHandler();

        registry.register(SERVICE_TYPE, coreHandler, core);
        registry.register(SERVICE_TYPE, new AlternateSecurityHandler(), plugin);

        assertSame(core, registry.getDefinition(SERVICE_TYPE));
        assertSame(coreHandler, registry.get(SERVICE_TYPE));
        assertEquals(
                "reserved core implementation has highest precedence",
                registry.getResolution(SERVICE_TYPE).reason());
    }

    @Test
    void diagnosticsExposeWinnerCandidatesAndReason() {
        ServiceRegistry registry = new ServiceRegistry();
        ServiceCallDefinition local = localDefinition(
                SERVICE_TYPE,
                TestSecurityHandler.class,
                "plugin:local");
        registry.register(SERVICE_TYPE, new TestSecurityHandler(), local);
        registerServerFunction(
                registry,
                dbDefinition(SERVICE_TYPE, "api_key_create"));

        JsonObject params = new JsonObject();
        params.addProperty("serviceType", SERVICE_TYPE);
        JsonObject diagnostics =
                new RegistryListHandler(registry).execute(params, "registryList", "test");
        JsonObject resolution = diagnostics.getAsJsonArray("resolutions")
                .get(0)
                .getAsJsonObject();

        assertEquals(
                "local annotation handler overrides server-backed generic function",
                resolution.get("reason").getAsString());
        assertEquals(
                "local_annotation",
                resolution.getAsJsonObject("selected").get("origin").getAsString());
        assertEquals(2, resolution.getAsJsonArray("candidates").size());
    }

    @Test
    void explicitCompatibleOverrideWinsInEveryRegistrationOrder() {
        for (int seed = 0; seed < 100; seed++) {
            ServiceRegistry registry = new ServiceRegistry();
            ServiceCallDefinition generated = generatedContractDefinition();
            ServiceCallDefinition override = compatibleOverrideDefinition(
                    AlternateSecurityHandler.class,
                    "custom-security",
                    "2.0.0");
            AlternateSecurityHandler overrideHandler =
                    new AlternateSecurityHandler();

            List<Consumer<ServiceRegistry>> registrations = new ArrayList<>();
            registrations.add(target -> target.register(
                    SERVICE_TYPE,
                    new GenericProcessHandler(),
                    generated,
                    FunctionRegistrationOrigin.LOCAL_REGISTRY,
                    "generated-security"));
            registrations.add(target -> target.register(
                    SERVICE_TYPE,
                    overrideHandler,
                    override,
                    FunctionRegistrationOrigin.LOCAL_ANNOTATION,
                    "custom-security"));
            Collections.shuffle(registrations, new Random(seed));
            registrations.forEach(registration -> registration.accept(registry));
            registry.validateExplicitOverrides();

            FunctionResolution resolution = registry.getResolution(SERVICE_TYPE);
            assertSame(override, registry.getDefinition(SERVICE_TYPE));
            assertSame(overrideHandler, registry.get(SERVICE_TYPE));
            assertEquals(FunctionOverrideEvidence.Status.VALID,
                    resolution.overrideEvidence().status());
            assertEquals("generated-security",
                    resolution.overrideEvidence().targetPluginId());
            assertTrue(resolution.reason().startsWith(
                    "explicit plugin override validated"));
        }
    }

    @Test
    void removingExplicitOverrideRestoresGeneratedFallback() {
        ServiceRegistry registry = registryWithCompatibleOverride();

        registry.deregisterPlugin("custom-security");
        registry.validateExplicitOverrides();

        FunctionResolution resolution = registry.getResolution(SERVICE_TYPE);
        assertEquals("generated-security", resolution.selected().pluginId());
        assertEquals(FunctionCandidate.Kind.DB_FUNCTION,
                resolution.selected().kind());
        assertInstanceOf(GenericProcessHandler.class, registry.get(SERVICE_TYPE));
    }

    @Test
    void serverRegistryCopyRetainsOverrideTargetAcrossRestart() {
        ServiceRegistry registry = new ServiceRegistry();
        ServiceCallDefinition generated = generatedContractDefinition();
        ServiceCallDefinition override = compatibleOverrideDefinition(
                AlternateSecurityHandler.class,
                "custom-security",
                "2.0.0");

        registry.register(
                SERVICE_TYPE,
                new AlternateSecurityHandler(),
                override,
                FunctionRegistrationOrigin.LOCAL_ANNOTATION,
                "custom-security");
        registry.register(
                SERVICE_TYPE,
                new GenericProcessHandler(),
                generated,
                FunctionRegistrationOrigin.SERVER_REGISTRY);
        registry.validateExplicitOverrides();

        FunctionResolution resolution = registry.getResolution(SERVICE_TYPE);
        assertSame(override, registry.getDefinition(SERVICE_TYPE));
        assertEquals("generated-security",
                resolution.overrideEvidence().targetPluginId());
        assertEquals("1.5.0",
                resolution.overrideEvidence().targetPluginVersion());
    }

    @Test
    void competingExplicitOverridesFailClearly() {
        ServiceRegistry registry = registryWithCompatibleOverride();
        ServiceCallDefinition competitor = compatibleOverrideDefinition(
                TestSecurityHandler.class,
                "competing-security",
                "3.0.0");

        AmbiguousFunctionRegistrationException error = assertThrows(
                AmbiguousFunctionRegistrationException.class,
                () -> registry.register(
                        SERVICE_TYPE,
                        new TestSecurityHandler(),
                        competitor,
                        FunctionRegistrationOrigin.LOCAL_ANNOTATION,
                        "competing-security"));

        assertTrue(error.getMessage().contains("custom-security"));
        assertTrue(error.getMessage().contains("competing-security"));
    }

    @Test
    void incompatibleOverrideSignatureFailsClearly() {
        ServiceCallDefinition override = compatibleOverrideDefinition(
                AlternateSecurityHandler.class,
                "custom-security",
                "2.0.0");
        override.getAnnotations().functionContract.signature =
                "api-key-create-v2";

        InvalidFunctionOverrideException error = assertIncompatible(override);

        assertTrue(error.getMessage().contains("signature"));
        assertTrue(error.getMessage().contains("api-key-create-v2"));
    }

    @Test
    void incompatibleOverrideCapabilitiesFailClearly() {
        ServiceCallDefinition override = compatibleOverrideDefinition(
                AlternateSecurityHandler.class,
                "custom-security",
                "2.0.0");
        override.getAnnotations().functionContract.capabilities =
                List.of("service_call");

        InvalidFunctionOverrideException error = assertIncompatible(override);

        assertTrue(error.getMessage().contains("capabilities"));
        assertTrue(error.getMessage().contains("jsonb"));
    }

    @Test
    void incompatibleOverrideVersionRangeFailsClearly() {
        ServiceCallDefinition override = compatibleOverrideDefinition(
                AlternateSecurityHandler.class,
                "custom-security",
                "2.0.0");
        override.getAnnotations().functionOverride.versionRange =
                "[2.0.0,3.0.0)";

        InvalidFunctionOverrideException error = assertIncompatible(override);

        assertTrue(error.getMessage().contains("versionRange"));
        assertTrue(error.getMessage().contains("1.2.0"));
    }

    @Test
    void incompatibleOverrideNamespaceFailsClearly() {
        ServiceCallDefinition override = compatibleOverrideDefinition(
                AlternateSecurityHandler.class,
                "custom-security",
                "2.0.0");
        override.getAnnotations().functionContract.namespace =
                "security.wrong";

        InvalidFunctionOverrideException error = assertIncompatible(override);

        assertTrue(error.getMessage().contains("namespace"));
        assertTrue(error.getMessage().contains("security.access_control"));
    }

    @Test
    void contractBearingGeneratedFunctionRejectsImplicitCollision() {
        ServiceRegistry registry = new ServiceRegistry();
        registry.register(
                SERVICE_TYPE,
                new GenericProcessHandler(),
                generatedContractDefinition(),
                FunctionRegistrationOrigin.LOCAL_REGISTRY,
                "generated-security");

        InvalidFunctionOverrideException error = assertThrows(
                InvalidFunctionOverrideException.class,
                () -> registry.register(
                        SERVICE_TYPE,
                        new AlternateSecurityHandler(),
                        localDefinition(
                                SERVICE_TYPE,
                                AlternateSecurityHandler.class,
                                "plugin:custom-security"),
                        FunctionRegistrationOrigin.LOCAL_ANNOTATION,
                        "custom-security"));

        assertTrue(error.getMessage().contains(
                "declares no explicit functionOverride relationship"));
    }

    @Test
    void strictValidationRejectsMissingOverrideTarget() {
        ServiceRegistry registry = new ServiceRegistry();
        registry.register(
                SERVICE_TYPE,
                new AlternateSecurityHandler(),
                compatibleOverrideDefinition(
                        AlternateSecurityHandler.class,
                        "custom-security",
                        "2.0.0"),
                FunctionRegistrationOrigin.LOCAL_ANNOTATION,
                "custom-security");

        assertEquals(FunctionOverrideEvidence.Status.PENDING_TARGET,
                registry.getResolution(SERVICE_TYPE)
                        .overrideEvidence()
                        .status());
        InvalidFunctionOverrideException error = assertThrows(
                InvalidFunctionOverrideException.class,
                registry::validateExplicitOverrides);
        assertTrue(error.getMessage().contains(
                "cannot find generated target plugin 'generated-security'"));
    }

    @Test
    void diagnosticsExposeValidatedOverrideEvidence() {
        ServiceRegistry registry = registryWithCompatibleOverride();
        JsonObject params = new JsonObject();
        params.addProperty("serviceType", SERVICE_TYPE);

        JsonObject diagnostics =
                new RegistryListHandler(registry).execute(
                        params,
                        "registryList",
                        "test");
        JsonObject resolution = diagnostics.getAsJsonArray("resolutions")
                .get(0)
                .getAsJsonObject();
        JsonObject evidence = resolution.getAsJsonObject("overrideEvidence");

        assertEquals("valid", evidence.get("status").getAsString());
        assertEquals("generated-security",
                evidence.get("targetPluginId").getAsString());
        assertEquals("1.2.0",
                evidence.get("targetFunctionVersion").getAsString());
        assertTrue(evidence.get("namespaceCompatible").getAsBoolean());
        assertTrue(evidence.get("signatureCompatible").getAsBoolean());
        assertTrue(evidence.get("capabilitiesCompatible").getAsBoolean());
        assertTrue(evidence.get("versionCompatible").getAsBoolean());
    }

    private static ServiceRegistry registryWithCompatibleOverride() {
        ServiceRegistry registry = new ServiceRegistry();
        registry.register(
                SERVICE_TYPE,
                new GenericProcessHandler(),
                generatedContractDefinition(),
                FunctionRegistrationOrigin.LOCAL_REGISTRY,
                "generated-security");
        registry.register(
                SERVICE_TYPE,
                new AlternateSecurityHandler(),
                compatibleOverrideDefinition(
                        AlternateSecurityHandler.class,
                        "custom-security",
                        "2.0.0"),
                FunctionRegistrationOrigin.LOCAL_ANNOTATION,
                "custom-security");
        registry.validateExplicitOverrides();
        return registry;
    }

    private static InvalidFunctionOverrideException assertIncompatible(
            ServiceCallDefinition override) {
        ServiceRegistry registry = new ServiceRegistry();
        registry.register(
                SERVICE_TYPE,
                new GenericProcessHandler(),
                generatedContractDefinition(),
                FunctionRegistrationOrigin.LOCAL_REGISTRY,
                "generated-security");
        return assertThrows(
                InvalidFunctionOverrideException.class,
                () -> registry.register(
                        SERVICE_TYPE,
                        new AlternateSecurityHandler(),
                        override,
                        FunctionRegistrationOrigin.LOCAL_ANNOTATION,
                        "custom-security"));
    }

    private static ServiceCallDefinition generatedContractDefinition() {
        ServiceCallDefinition definition =
                dbDefinition(SERVICE_TYPE, "api_key_create");
        applyContract(
                definition,
                "generated-security",
                "1.5.0",
                "1.2.0",
                "api-key-create-v1",
                List.of("service_call", "jsonb"));
        return definition;
    }

    private static ServiceCallDefinition compatibleOverrideDefinition(
            Class<? extends ServiceCallHandler> handlerClass,
            String pluginId,
            String pluginVersion) {
        ServiceCallDefinition definition = localDefinition(
                SERVICE_TYPE,
                handlerClass,
                "plugin:" + pluginId);
        applyContract(
                definition,
                pluginId,
                pluginVersion,
                "1.2.0",
                "api-key-create-v1",
                List.of("service_call", "jsonb", "audit"));
        definition.getAnnotations().functionOverride =
                new ServiceCallDefinition.FunctionOverrideMeta();
        definition.getAnnotations().functionOverride.targetServiceType =
                SERVICE_TYPE;
        definition.getAnnotations().functionOverride.targetPluginId =
                "generated-security";
        definition.getAnnotations().functionOverride.versionRange =
                "[1.0.0,2.0.0)";
        return definition;
    }

    private static void applyContract(
            ServiceCallDefinition definition,
            String pluginId,
            String pluginVersion,
            String functionVersion,
            String signature,
            List<String> capabilities) {
        definition.getAnnotations().functionContract =
                new ServiceCallDefinition.FunctionContractMeta();
        ServiceCallDefinition.FunctionContractMeta contract =
                definition.getAnnotations().functionContract;
        contract.namespace = "security.access_control";
        contract.version = functionVersion;
        contract.signature = signature;
        contract.capabilities = capabilities;
        contract.pluginId = pluginId;
        contract.pluginVersion = pluginVersion;
    }

    private static void assertSelectedLocalHandler(
            ServiceRegistry registry,
            ServiceCallDefinition local,
            ServiceCallHandler localHandler) {
        assertSame(local, registry.getDefinition(SERVICE_TYPE));
        assertSame(localHandler, registry.get(SERVICE_TYPE));
        FunctionResolution resolution = registry.getResolution(SERVICE_TYPE);
        assertEquals(2, resolution.candidates().size());
        assertEquals(
                "local annotation handler overrides server-backed generic function",
                resolution.reason());
    }

    private static void registerServerFunction(
            ServiceRegistry registry,
            ServiceCallDefinition definition) {
        registry.register(
                definition.getServiceType(),
                new GenericProcessHandler(),
                definition,
                FunctionRegistrationOrigin.SERVER_REGISTRY);
    }

    private static ServiceCallDefinition localDefinition(
            String serviceType,
            Class<? extends ServiceCallHandler> handlerClass,
            String source) {
        ServiceCallDefinition definition = new ServiceCallDefinition();
        definition.setServiceType(serviceType);
        definition.setHandlerClass(handlerClass.getName());
        definition.setTargetMethodName("execute");
        definition.setSource(source);
        definition.setActive(true);
        return definition;
    }

    private static ServiceCallDefinition dbDefinition(
            String serviceType,
            String functionName) {
        ServiceCallDefinition definition = new ServiceCallDefinition();
        definition.setServiceType(serviceType);
        definition.setHandlerClass(GenericProcessHandler.class.getSimpleName());
        definition.setSource("db:EveryArrow");
        definition.setDbDatabase("EveryArrow");
        definition.setDbSchema("app_security");
        definition.setDbFunction(functionName);
        definition.setActive(true);
        return definition;
    }

    static final class TestSecurityHandler implements ServiceCallHandler {
        @Override
        public JsonElement execute(JsonObject params, String type, String source) {
            return new JsonObject();
        }
    }

    static final class AlternateSecurityHandler implements ServiceCallHandler {
        @Override
        public JsonElement execute(JsonObject params, String type, String source) {
            return new JsonObject();
        }
    }
}
