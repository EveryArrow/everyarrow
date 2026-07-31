/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.tests.core;

import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.eventengine.core.credits.ExecutionCreditServiceContract;
import com.everyarrow.eventengine.core.hooks.SecurityHook;
import com.everyarrow.eventengine.core.security.SecurityServiceContract;
import com.everyarrow.eventengine.core.services.registry.ServiceRegistry;
import com.everyarrow.eventengine.core.services.runtime.SecurityContext;
import com.everyarrow.eventengine.core.services.runtime.ServiceCall;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that after Engine.initialize():
 *  - public core registries are loaded from Event Manager
 *  - internal and source-owned plugin registries are not bundled here
 *  - All registry service types are indexed
 *
 * No DB connection required — tests only inspect the registry state.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CoreJsonRegistriesEngineTest {

    private Engine engine;
    private ServiceRegistry registry;

    @BeforeAll
    void bootEngine() throws Exception {
        Path emptyPluginRoot = Files.createTempDirectory("ea-empty-plugin-root");
        engine = new Engine(emptyPluginRoot.toFile());
        engine.initialize();
        registry = engine.getRegistry();
    }

    @AfterAll
    void shutdownEngine() {
        if (engine != null) engine.shutdown();
    }

    // -------------------------------------------------------------------------
    // Registry is populated
    // -------------------------------------------------------------------------

    @Test
    void registryIsNotEmpty() {
        Set<String> types = registry.listServiceTypes();
        assertFalse(types.isEmpty(), "Registry should be populated after Engine.initialize()");
    }

    @Test
    void registryDoesNotBundlePerchPlatformServices() {
        Set<String> types = registry.listServiceTypes();
        assertFalse(types.contains("perch.telemetry.start_job"),
                "Perch lifecycle services should come from a staged internal plugin");
    }

    @Test
    void registryDoesNotBundleSecurityPlatformServices() {
        Set<String> types = registry.listServiceTypes();
        assertFalse(types.contains(SecurityServiceContract.CHECK_USER_ACCESS),
                "Security implementation services should come from a staged internal plugin");
    }

    @Test
    void registryDoesNotLoadSourceOwnedFoundryDemoFromEventManager() {
        Set<String> types = registry.listServiceTypes();

        long foundryCount = types.stream().filter(t -> t.startsWith("everyarrow.event_manager.")).count();

        assertEquals(0, foundryCount,
                "Foundry demo registry entries should come from the staged foundry-plugin artifact, not a checked-in ea-event-manager plugin folder");
    }

    @Test
    void internalPlatformPluginsAreOptionalAndNotBundled() {
        Set<String> pluginNames = engine.getPluginManager().getLoadedPluginNames();

        assertFalse(pluginNames.contains("perch"),
                "Perch should be staged from the internal repository when required");
        assertFalse(pluginNames.contains("security"),
                "Security providers should be staged from the internal repository when required");
        assertFalse(pluginNames.contains("foundry-dev-demo"),
                "product-specific registries must not be built into public Event Manager");
    }

    @Test
    void engineRegistersBuiltInSecurityHook() {
        assertTrue(engine.getHookManager().hasHook(SecurityHook.class),
                "Engine should register SecurityHook as a built-in hook");
    }

    @Test
    void healthRouteIsExplicitlyPublic() {
        ServiceCallDefinition def = registry.getDefinition("healthCheck");
        assertNotNull(def, "Health check definition must exist");
        assertNotNull(def.getAnnotations().secured, "Health check should carry secured metadata");
        assertTrue(def.getAnnotations().secured.isPublic, "Health check should be explicitly marked public");
    }

    @Test
    void executionCreditStatusRoutesAreRegistered() {
        ServiceCallDefinition status = registry.findRestRoute("GET", "/execution-credits/status");
        ServiceCallDefinition usage = registry.findRestRoute("GET", "/execution-credits/usage");
        ServiceCallDefinition licenseImport = registry.findRestRoute("POST", "/execution-licenses/import");

        assertNotNull(status, "Execution credit status REST route must be registered");
        assertNotNull(usage, "Execution credit usage REST route must be registered");
        assertNotNull(licenseImport, "Execution license import REST route must be registered");
        assertEquals(ExecutionCreditServiceContract.STATUS, status.getServiceType());
        assertEquals(ExecutionCreditServiceContract.USAGE_SUMMARY, usage.getServiceType());
        assertEquals(ExecutionCreditServiceContract.LICENSE_IMPORT, licenseImport.getServiceType());
        assertEquals(ExecutionCreditServiceContract.APPLICATION_KEY,
                status.getAnnotations().call.applicationKey);
        assertEquals(ExecutionCreditServiceContract.APPLICATION_KEY,
                usage.getAnnotations().call.applicationKey);
        assertEquals(ExecutionCreditServiceContract.APPLICATION_KEY,
                licenseImport.getAnnotations().call.applicationKey);
    }

    @Test
    void securityHookAttachesHintsWithoutInternalPluginMetadata() throws Exception {
        ServiceCall call = new ServiceCall();
        call.setType(SecurityServiceContract.RESOLVE_PRINCIPAL);

        JsonObject params = new JsonObject();
        params.addProperty("auth_provider_id", "oidc|user-123");
        call.setParams(params);

        engine.getHookManager().before(call);

        SecurityContext context = call.getSecurityContext();
        assertNotNull(context, "SecurityHook should attach a SecurityContext to each call");
        assertEquals(SecurityContext.CredentialType.NONE, context.getCredentialType());
        assertEquals("oidc|user-123", context.getAuthProviderId());
        assertEquals(SecurityServiceContract.RESOLVE_PRINCIPAL, context.getServiceType());
        assertNull(context.getDefinitionSource(),
                "The public core should not synthesize staged Security definition metadata");
        assertFalse(context.isAuthenticated(), "Hints alone should not mark the call authenticated yet");
    }

    // -------------------------------------------------------------------------
    // Total loaded count sanity check
    // -------------------------------------------------------------------------

    @Test
    void totalRegistryCountIsReasonableForPublicCoreServices() {
        // Core JSON registry plus core/annotated handlers. Internal and broad source-owned plugin
        // registries should not be bundled in this public-release surface.
        long total = registry.listDefinitions().size();
        assertTrue(total >= 4, "Registry should include core definitions, got " + total);
        assertTrue(total < 150, "Registry should only include core and curated platform definitions, got " + total);
    }
}
