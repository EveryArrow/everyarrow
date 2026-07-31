/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.registry;

import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Stores the last database-loaded registry as a surface-scoped local JSON snapshot.
 */
public final class LocalRegistrySnapshotStore {

    public static final String SNAPSHOT_PATH_CONFIG = "EA_REGISTRY_SNAPSHOT_PATH";
    private static final int FORMAT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path snapshotPath;

    public LocalRegistrySnapshotStore(Path snapshotPath) {
        this.snapshotPath = Objects.requireNonNull(snapshotPath, "snapshotPath is required")
                .toAbsolutePath()
                .normalize();
    }

    public Path path() {
        return snapshotPath;
    }

    public Optional<List<ServiceCallDefinition>> load(String surfaceId) throws IOException {
        if (!Files.isRegularFile(snapshotPath)) {
            return Optional.empty();
        }

        JsonElement parsed = JsonParser.parseString(Files.readString(snapshotPath, StandardCharsets.UTF_8));
        if (!parsed.isJsonObject()) {
            throw new IOException("Registry snapshot root must be a JSON object: " + snapshotPath);
        }

        JsonObject root = parsed.getAsJsonObject();
        int version = integer(root, "format_version", 0);
        if (version != FORMAT_VERSION) {
            throw new IOException("Unsupported registry snapshot format " + version + ": " + snapshotPath);
        }

        String expectedSurface = normalizeSurfaceId(surfaceId);
        String storedSurface = string(root, "surface_id");
        if (!expectedSurface.equalsIgnoreCase(normalizeSurfaceId(storedSurface))) {
            throw new IOException("Registry snapshot belongs to surface '" + storedSurface
                    + "', not '" + expectedSurface + "': " + snapshotPath);
        }

        JsonElement definitionsElement = root.get("definitions");
        if (definitionsElement == null || !definitionsElement.isJsonArray()) {
            throw new IOException("Registry snapshot is missing definitions: " + snapshotPath);
        }

        List<ServiceCallDefinition> definitions = new ArrayList<>();
        for (JsonElement element : definitionsElement.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            ServiceCallDefinition definition = GSON.fromJson(element, ServiceCallDefinition.class);
            if (definition != null
                    && definition.getServiceType() != null
                    && !definition.getServiceType().isBlank()) {
                definitions.add(definition);
            }
        }
        return Optional.of(List.copyOf(definitions));
    }

    public void save(String surfaceId, List<ServiceCallDefinition> definitions) throws IOException {
        Path parent = snapshotPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        JsonObject root = new JsonObject();
        root.addProperty("format_version", FORMAT_VERSION);
        root.addProperty("surface_id", normalizeSurfaceId(surfaceId));
        root.addProperty("captured_at", Instant.now().toString());

        JsonArray serializedDefinitions = new JsonArray();
        if (definitions != null) {
            for (ServiceCallDefinition definition : definitions) {
                if (definition != null
                        && definition.getServiceType() != null
                        && !definition.getServiceType().isBlank()) {
                    serializedDefinitions.add(GSON.toJsonTree(definition));
                }
            }
        }
        root.add("definitions", serializedDefinitions);

        Path temporary = temporaryPath();
        Files.writeString(temporary, GSON.toJson(root), StandardCharsets.UTF_8);
        moveAtomically(temporary, snapshotPath);
    }

    private Path temporaryPath() {
        String fileName = snapshotPath.getFileName().toString();
        return snapshotPath.resolveSibling(fileName + ".tmp");
    }

    private void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private int integer(JsonObject object, String key, int fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String string(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        try {
            return object.get(key).getAsString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalizeSurfaceId(String surfaceId) {
        return surfaceId == null || surfaceId.isBlank() ? "default" : surfaceId.trim();
    }
}
