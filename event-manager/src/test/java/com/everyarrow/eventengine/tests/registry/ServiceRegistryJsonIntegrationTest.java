/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.tests.registry;

import com.everyarrow.eventengine.core.services.registry.JsonServiceRegistryLoader;
import com.everyarrow.eventengine.core.services.registry.ServiceRegistry;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: loads JSON registry into a real ServiceRegistry
 * and verifies route resolution, listing, and metadata.
 *
 * No Engine bootstrap, no HTTP server, no DB — just ServiceRegistry + JSON
 * loader.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ServiceRegistryJsonIntegrationTest {

    private ServiceRegistry registry;
    private int loadedCount;

    @BeforeAll
    void loadRegistryFromJson() {
        registry = new ServiceRegistry();
        List<ServiceCallDefinition> defs = JsonServiceRegistryLoader.loadFromClasspath("service_registry_db.json");
        assertFalse(defs.isEmpty(), "service_registry_db.json should be loadable from classpath");

        for (ServiceCallDefinition def : defs) {
            registry.registerDefinition(def);
        }
        loadedCount = defs.size();

        System.out.println("[Test] Loaded " + loadedCount + " definitions into ServiceRegistry");
    }

    // -------------------------------------------------------------------------
    // Registry population
    // -------------------------------------------------------------------------

    @Test
    void testRegistryPopulatedFromJson() {
        Set<String> types = registry.listServiceTypes();
        assertFalse(types.isEmpty(), "Registry should have service types after JSON load");
        // There may be fewer unique types than definitions (if multiple schemas have
        // same key structure)
        assertTrue(types.size() > 0, "Should have at least one service type");
    }

    @Test
    void testListDefinitionsCount() {
        Collection<ServiceCallDefinition> all = registry.listDefinitions();
        assertEquals(loadedCount, all.size(), "All loaded definitions should be in registry");
    }

    // -------------------------------------------------------------------------
    // Exact REST route resolution
    // -------------------------------------------------------------------------

    @Test
    void testExactRestRouteResolution_batchEvent() {
        ServiceCallDefinition def = registry.findRestRoute("POST", "/batch-event");
        assertNotNull(def, "Should resolve POST /batch-event");
        assertEquals("everyarrow.registry.batch_event", def.getServiceType());
    }

    @Test
    void testExactRestRouteResolution_createEntitlementPlan() {
        ServiceCallDefinition def = registry.findRestRoute("POST", "/entitlement/plan");
        assertNotNull(def, "Should resolve POST /entitlement/plan");
        assertTrue(def.getServiceType().contains("plan_create"),
                "Should resolve to plan_create service type");
    }

    @Test
    void testExactRestRouteResolution_caseInsensitiveMethod() {
        // findRestRoute normalizes method to uppercase
        ServiceCallDefinition def = registry.findRestRoute("post", "/batch-event");
        assertNotNull(def, "Should resolve post (lowercase) /batch-event");
    }

    @Test
    void testExactRestRouteResolution_unknownRouteReturnsNull() {
        ServiceCallDefinition def = registry.findRestRoute("GET", "/this-route-does-not-exist");
        assertNull(def, "Should return null for unknown route");
    }

    // -------------------------------------------------------------------------
    // Templated REST route resolution
    // -------------------------------------------------------------------------

    @Test
    void testTemplatedRouteResolution_deleteWithId() {
        ServiceCallDefinition def = registry.findRestRoute("DELETE", "/customer/some-uuid-here");
        assertNotNull(def, "Should resolve DELETE /customer/{id} with actual ID");
        assertEquals("security.access_control.customer_delete", def.getServiceType());
    }

    @Test
    void testTemplatedRouteResolution_extractsPathParams() {
        var match = registry.findRestRouteMatch("DELETE", "/customer/abc-123");
        assertNotNull(match, "Should resolve DELETE /customer/{id}");
        assertEquals("abc-123", match.pathParams().get("id"),
                "Should extract path param 'id'");
    }

    // -------------------------------------------------------------------------
    // Definition metadata
    // -------------------------------------------------------------------------

    @Test
    void testDefinitionMetadata_category() {
        ServiceCallDefinition def = registry.getDefinition("everyarrow.registry.batch_event");
        assertNotNull(def, "Should find definition by service type");
        assertEquals("Database", def.getCategory());
    }

    @Test
    void testDefinitionMetadata_source() {
        ServiceCallDefinition def = registry.getDefinition("everyarrow.registry.batch_event");
        assertNotNull(def);
        assertEquals("db:EveryArrow", def.getSource());
    }

    @Test
    void testDefinitionMetadata_handlerClass() {
        ServiceCallDefinition def = registry.getDefinition("everyarrow.registry.batch_event");
        assertNotNull(def);
        assertEquals("GenericProcessHandler", def.getHandlerClass());
    }

    @Test
    void testDefinitionMetadata_active() {
        ServiceCallDefinition def = registry.getDefinition("everyarrow.registry.batch_event");
        assertNotNull(def);
        assertTrue(def.isActive());
    }

    // -------------------------------------------------------------------------
    // Source-owned plugin databases
    // -------------------------------------------------------------------------

    @Test
    void testAppSecurityBehaviorIsSourceOwnedByBuiltInPlugin() {
        ServiceCallDefinition def = registry.getDefinition("security.access_control.check_user_access");
        assertNull(def, "app-security behavior services should arrive from the built-in security plugin");
    }

    @Test
    void testPerchDevIsSourceOwnedByPlugin() {
        Collection<ServiceCallDefinition> all = registry.listDefinitions();
        boolean hasPerch = all.stream()
                .anyMatch(d -> d.getServiceType() != null && d.getServiceType().startsWith("perch.telemetry."));
        assertFalse(hasPerch, "telemetry services should arrive from the staged perch plugin");
    }
}
