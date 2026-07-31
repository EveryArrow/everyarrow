/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.registry;

import com.everyarrow.eventengine.spi.model.DbDiscoveredService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;

/**
 * Writes canonical DB-discovered service entries to JSON files.
 */
public final class JsonServiceRegistryWriter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private JsonServiceRegistryWriter() {
    }

    public static void write(Path targetPath, List<DbDiscoveredService> services) throws IOException {
        Objects.requireNonNull(targetPath, "targetPath is required");
        Objects.requireNonNull(services, "services are required");

        Path normalized = targetPath.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(
                normalized,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            GSON.toJson(sanitize(services), writer);
        }
    }

    private static List<DbDiscoveredService> sanitize(List<DbDiscoveredService> services) {
        return services.stream()
                .map(JsonServiceRegistryWriter::withoutDefinition)
                .toList();
    }

    private static DbDiscoveredService withoutDefinition(DbDiscoveredService service) {
        if (service == null) {
            return null;
        }

        return new DbDiscoveredService(
                service.database(),
                service.schema(),
                service.function(),
                service.serviceType(),
                service.httpMethod(),
                service.routePath(),
                service.description(),
                null,
                service.jsonType(),
                service.annotations());
    }
}
