/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.tests.registry;

import com.everyarrow.eventengine.core.services.registry.JsonServiceRegistryLoader;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.everyarrow.utilities.environment.Environment;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JsonServiceRegistryLoader.
 *
 * Tests the loader in isolation — no Engine bootstrap, no DB connection.
 */
class JsonServiceRegistryLoaderTest {

    @AfterEach
    void resetEnvironment() {
        Environment.reset();
    }

    // -------------------------------------------------------------------------
    // Classpath loading
    // -------------------------------------------------------------------------

    @Test
    void testLoadFromClasspath_loadsAllEntries() {
        List<ServiceCallDefinition> defs = JsonServiceRegistryLoader.loadFromClasspath("service_registry_db.json");
        assertFalse(defs.isEmpty(), "Expected non-empty definitions from service_registry_db.json");
        assertEquals(4, defs.size(),
                "Test classpath registry should contain only sanitized fixture entries");
    }

    @Test
    void testLoadFromClasspath_serviceTypesParsed() {
        List<ServiceCallDefinition> defs = JsonServiceRegistryLoader.loadFromClasspath("service_registry_db.json");

        Set<String> types = defs.stream()
                .map(ServiceCallDefinition::getServiceType)
                .collect(Collectors.toSet());

        // Spot-check known service types from the dump
        assertTrue(types.contains("everyarrow.registry.batch_event"),
                "Should contain everyarrow.registry.batch_event");
        assertTrue(types.contains("security.access_control.customer_create"),
                "Should contain security.access_control.customer_create");
        assertFalse(types.stream().anyMatch(type -> type.equals("security.access_control.check_user_access")),
                "App-security behavior entries should arrive from the built-in security plugin");
        assertFalse(types.stream().anyMatch(type -> type.startsWith("perch.telemetry.")),
                "Perch entries should arrive from the staged perch plugin");
    }

    @Test
    void testLoadFromClasspath_routesAndMethodsParsed() {
        List<ServiceCallDefinition> defs = JsonServiceRegistryLoader.loadFromClasspath("service_registry_db.json");

        // Find a specific definition and verify its REST metadata
        ServiceCallDefinition batchEvent = defs.stream()
                .filter(d -> "everyarrow.registry.batch_event".equals(d.getServiceType()))
                .findFirst()
                .orElse(null);

        assertNotNull(batchEvent, "batch_event definition should exist");
        assertTrue(batchEvent.hasRestRoute(), "batch_event should have a REST route");
        assertEquals("POST", batchEvent.getRestRoute().method, "batch_event should be POST");
        assertEquals("/batch-event", batchEvent.getRestRoute().path, "batch_event route should be /batch-event");
    }

    @Test
    void testLoadFromClasspath_definitionMetadata() {
        List<ServiceCallDefinition> defs = JsonServiceRegistryLoader.loadFromClasspath("service_registry_db.json");

        ServiceCallDefinition def = defs.stream()
                .filter(d -> "security.access_control.customer_create".equals(d.getServiceType()))
                .findFirst()
                .orElse(null);

        assertNotNull(def, "customer_create should exist");
        assertEquals("Database", def.getCategory(), "Category should be 'Database'");
        assertEquals("db:EveryArrow", def.getSource(), "Source should be 'db:EveryArrow'");
        assertEquals("GenericProcessHandler", def.getHandlerClass(), "HandlerClass should be 'GenericProcessHandler'");
        assertTrue(def.isActive(), "Should be active");
    }

    @Test
    void testLoadFromClasspath_deleteRoutesHaveTemplatedPaths() {
        List<ServiceCallDefinition> defs = JsonServiceRegistryLoader.loadFromClasspath("service_registry_db.json");

        ServiceCallDefinition deleteCust = defs.stream()
                .filter(d -> "security.access_control.customer_delete".equals(d.getServiceType()))
                .findFirst()
                .orElse(null);

        assertNotNull(deleteCust, "customer_delete should exist");
        assertTrue(deleteCust.hasRestRoute(), "customer_delete should have a REST route");
        assertEquals("DELETE", deleteCust.getRestRoute().method);
        assertEquals("/customer/{id}", deleteCust.getRestRoute().path,
                "customer_delete should have a templated path");
    }

    // -------------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------------

    @Test
    void testLoadFromClasspath_missingResourceReturnsEmptyList() {
        List<ServiceCallDefinition> defs = JsonServiceRegistryLoader.loadFromClasspath("nonexistent_file.json");
        assertNotNull(defs, "Should return non-null list");
        assertTrue(defs.isEmpty(), "Should return empty list for missing resource");
    }

    @Test
    void testLoadFromFile_missingFileReturnsEmptyList() {
        List<ServiceCallDefinition> defs = JsonServiceRegistryLoader.loadFromFile("/tmp/does_not_exist_12345.json");
        assertNotNull(defs, "Should return non-null list");
        assertTrue(defs.isEmpty(), "Should return empty list for missing file");
    }

    @Test
    void testLoadFromFile_emptyArrayReturnsEmptyList() throws Exception {
        File tmp = File.createTempFile("empty_registry_", ".json");
        tmp.deleteOnExit();
        try (FileWriter w = new FileWriter(tmp)) {
            w.write("[]");
        }

        List<ServiceCallDefinition> defs = JsonServiceRegistryLoader.loadFromFile(tmp.getAbsolutePath());
        assertNotNull(defs, "Should return non-null list");
        assertTrue(defs.isEmpty(), "Should return empty list for empty JSON array");
    }

    @Test
    void testLoadFromFile_validFileLoads() throws Exception {
        File tmp = File.createTempFile("test_registry_", ".json");
        tmp.deleteOnExit();
        try (FileWriter w = new FileWriter(tmp)) {
            w.write("""
                        [
                          {
                            "service_type": "test_db.public.hello_world",
                            "db_name": "test_db",
                            "schema_name": "public",
                            "function_name": "hello_world",
                            "http_method": "GET",
                            "route_path": "/hello",
                            "description": "Test function",
                            "annotations": {
                              "call": {
                                "applicationKey": "TESTS"
                              },
                              "secured": {
                                "roles": ["system"],
                                "scopes": ["tests:read"]
                              }
                            }
                          }
                        ]
                    """);
        }

        List<ServiceCallDefinition> defs = JsonServiceRegistryLoader.loadFromFile(tmp.getAbsolutePath());
        assertEquals(1, defs.size());
        assertEquals("test_db.public.hello_world", defs.get(0).getServiceType());
        assertEquals("GET", defs.get(0).getRestRoute().method);
        assertEquals("/hello", defs.get(0).getRestRoute().path);
    }

        @Test
        void testLoadFromFile_preservesOptionalSecurityMetadata() throws Exception {
                File tmp = File.createTempFile("test_registry_metadata_", ".json");
                tmp.deleteOnExit();
                try (FileWriter w = new FileWriter(tmp)) {
                        w.write("""
                                                [
                                                    {
                                                        "service_type": "test_db.public.public_demo",
                                                        "db_name": "test_db",
                                                        "schema_name": "public",
                                                        "function_name": "public_demo",
                                                        "http_method": "GET",
                                                        "route_path": "/demo",
                                                        "description": "Public demo function",
                                                        "annotations": {
                                                            "monitored": {
                                                                "application": "marketing",
                                                                "system": "demo-console"
                                                            },
                                                            "secured": {
                                                                "isPublic": true,
                                                                "roles": ["viewer"],
                                                                "scopes": ["demo:read"]
                                                            },
                                                            "call": {
                                                                "applicationKey": "MARKETING",
                                                                "open": true
                                                            },
                                                            "perch": {
                                                                "applicationName": "Marketing",
                                                                "systemName": "Demo",
                                                                "description": "Perch demo metadata"
                                                            }
                                                        }
                                                    }
                                                ]
                                        """);
                }

                List<ServiceCallDefinition> defs = JsonServiceRegistryLoader.loadFromFile(tmp.getAbsolutePath());

                assertEquals(1, defs.size());

                ServiceCallDefinition def = defs.get(0);
                assertEquals("test_db.public.public_demo", def.getServiceType());
                assertEquals("GET", def.getRestRoute().method);
                assertEquals("/demo", def.getRestRoute().path);
                assertNotNull(def.getAnnotations().monitored);
                assertEquals("marketing", def.getAnnotations().monitored.application);
                assertEquals("demo-console", def.getAnnotations().monitored.system);
                assertEquals("Public demo function", def.getAnnotations().monitored.description);
                assertNotNull(def.getAnnotations().secured);
                assertTrue(def.getAnnotations().secured.isPublic);
                assertEquals(List.of("viewer"), def.getAnnotations().secured.roles);
                assertEquals(List.of("demo:read"), def.getAnnotations().secured.scopes);
                assertNotNull(def.getAnnotations().call);
                assertEquals("MARKETING", def.getAnnotations().call.applicationKey);
                assertTrue(def.getAnnotations().call.open);
                assertNotNull(def.getAnnotations().perch);
                assertEquals("Marketing", def.getAnnotations().perch.applicationName);
                assertEquals("Demo", def.getAnnotations().perch.systemName);
                assertEquals("Perch demo metadata", def.getAnnotations().perch.description);
        }

        @Test
        void testLoadFromFile_rejectsRouteWithoutApplicationKey() throws Exception {
                File tmp = File.createTempFile("test_registry_missing_security_", ".json");
                tmp.deleteOnExit();
                try (FileWriter w = new FileWriter(tmp)) {
                        w.write("""
                                                [
                                                    {
                                                        "service_type": "test_db.public.hello_world",
                                                        "db_name": "test_db",
                                                        "schema_name": "public",
                                                        "function_name": "hello_world",
                                                        "http_method": "GET",
                                                        "route_path": "/hello",
                                                        "description": "Missing security metadata"
                                                    }
                                                ]
                                        """);
                }

                IllegalStateException error = assertThrows(IllegalStateException.class,
                                () -> JsonServiceRegistryLoader.loadFromFile(tmp.getAbsolutePath()));
                assertTrue(error.getMessage().contains("must declare annotations.call.applicationKey"));
        }

        @Test
        void testLoadFromFile_rejectsPublicDestructiveRoute() throws Exception {
                File tmp = File.createTempFile("test_registry_public_delete_", ".json");
                tmp.deleteOnExit();
                try (FileWriter w = new FileWriter(tmp)) {
                        w.write("""
                                                [
                                                    {
                                                        "service_type": "test_db.public.delete_hello",
                                                        "db_name": "test_db",
                                                        "schema_name": "public",
                                                        "function_name": "delete_hello",
                                                        "http_method": "DELETE",
                                                        "route_path": "/hello/{id}",
                                                        "description": "Public delete route",
                                                        "annotations": {
                                                          "call": {
                                                            "applicationKey": "TESTS",
                                                            "open": true
                                                          }
                                                        }
                                                    }
                                                ]
                                        """);
                }

                IllegalStateException ex = assertThrows(IllegalStateException.class,
                        () -> JsonServiceRegistryLoader.loadFromFile(tmp.getAbsolutePath()));

                assertTrue(ex.getMessage().contains("cannot be public"));
        }

        @Test
        void testLoadFromFile_rejectsUnallowlistedDbServiceWhenAllowlistConfigured() throws Exception {
                Environment.put("EA_DB_SERVICE_ALLOWLIST", "test_db.public.allowed_function");

                File tmp = File.createTempFile("test_registry_unallowlisted_", ".json");
                tmp.deleteOnExit();
                try (FileWriter w = new FileWriter(tmp)) {
                        w.write("""
                                                [
                                                    {
                                                        "service_type": "test_db.public.hello_world",
                                                        "db_name": "test_db",
                                                        "schema_name": "public",
                                                        "function_name": "hello_world",
                                                        "http_method": "GET",
                                                        "route_path": "/hello",
                                                        "description": "Unallowlisted function",
                                                        "annotations": {
                                                          "call": {
                                                            "applicationKey": "TESTS"
                                                          }
                                                        }
                                                    }
                                                ]
                                        """);
                }

                IllegalStateException ex = assertThrows(IllegalStateException.class,
                        () -> JsonServiceRegistryLoader.loadFromFile(tmp.getAbsolutePath()));

                assertTrue(ex.getMessage().contains("EA_DB_SERVICE_ALLOWLIST"));
        }
}
