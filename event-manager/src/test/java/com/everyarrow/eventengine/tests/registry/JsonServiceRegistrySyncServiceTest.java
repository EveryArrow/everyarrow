/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 *
 * Use is subject to the LICENSE file, including production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.tests.registry;

import com.everyarrow.eventengine.core.services.registry.JsonServiceRegistrySyncServiceImpl;
import com.everyarrow.eventengine.core.services.registry.JsonServiceRegistryWriter;
import com.everyarrow.eventengine.core.services.registry.ServiceRegistry;
import com.everyarrow.eventengine.spi.model.DbDiscoveredService;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.everyarrow.eventengine.spi.services.JsonServiceRegistrySyncService;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class JsonServiceRegistrySyncServiceTest {

    @Test
    void reloadFileReplacesPriorEntriesForSameManagedFile() throws Exception {
        ServiceRegistry registry = new ServiceRegistry();
        JsonServiceRegistrySyncService service = new JsonServiceRegistrySyncServiceImpl(null, registry);

        Path file = Files.createTempFile("registry-reload-", ".json");
        JsonServiceRegistryWriter.write(file, List.of(new DbDiscoveredService(
                "catalog_db",
                "public",
                "create_alpha",
                "catalog_db.public.create_alpha",
                "POST",
                "/alpha",
                "create alpha",
                null,
                "json",
                securedTestAnnotations())));

        JsonServiceRegistrySyncService.ReloadResult first = service.reloadFile(file);
        assertEquals(1, first.loadedCount());
        assertNotNull(registry.findRestRoute("POST", "/alpha"));

        JsonServiceRegistryWriter.write(file, List.of(new DbDiscoveredService(
                "catalog_db",
                "public",
                "create_beta",
                "catalog_db.public.create_beta",
                "POST",
                "/beta",
                "create beta",
                null,
                "json",
                securedTestAnnotations())));

        JsonServiceRegistrySyncService.ReloadResult second = service.reloadFile(file);

        assertEquals(1, second.loadedCount());
        assertNull(registry.findRestRoute("POST", "/alpha"),
                "Old route from same managed file should be removed on reload");
        assertNotNull(registry.findRestRoute("POST", "/beta"));
    }

    private ServiceCallDefinition.Annotations securedTestAnnotations() {
        ServiceCallDefinition.Annotations annotations = new ServiceCallDefinition.Annotations();
        annotations.call = new ServiceCallDefinition.CallMeta();
        annotations.call.applicationKey = "TESTS";
        annotations.secured = new ServiceCallDefinition.SecuredMeta();
        annotations.secured.roles = List.of("system");
        annotations.secured.scopes = List.of("tests:execute");
        return annotations;
    }
}
