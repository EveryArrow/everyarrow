/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.plugins;

import com.everyarrow.utilities.environment.Environment;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

final class PluginIntegrityVerifier {

    static final String INTEGRITY_MODE_CONFIG = "EA_PLUGIN_INTEGRITY_MODE";
    static final String INTEGRITY_MANIFEST_CONFIG = "EA_PLUGIN_INTEGRITY_MANIFEST";
    static final String DEFAULT_MANIFEST_NAME = "plugin-integrity.json";
    private static final String SHA_256 = "SHA-256";

    private final Path pluginRoot;
    private final Path manifestPath;
    private final Mode mode;

    PluginIntegrityVerifier(Path pluginRoot, Path manifestPath, Mode mode) {
        this.pluginRoot = pluginRoot.toAbsolutePath().normalize();
        this.manifestPath = manifestPath.toAbsolutePath().normalize();
        this.mode = mode;
    }

    static PluginIntegrityVerifier fromEnvironment(Path pluginRoot) {
        Environment environment = Environment.getInstance();
        Mode mode = Mode.parse(environment.get(INTEGRITY_MODE_CONFIG, Mode.REQUIRED.name()));
        String configuredManifest = environment.get(INTEGRITY_MANIFEST_CONFIG, "");
        Path manifest = configuredManifest == null || configuredManifest.isBlank()
                ? pluginRoot.resolve(DEFAULT_MANIFEST_NAME)
                : Path.of(configuredManifest.trim());
        return new PluginIntegrityVerifier(pluginRoot, manifest, mode);
    }

    void verify(List<java.io.File> jarFiles) throws IOException {
        if (mode == Mode.OFF || jarFiles.isEmpty()) {
            return;
        }

        try {
            JsonObject expectedDigests = loadExpectedDigests();
            for (java.io.File jarFile : jarFiles) {
                verifyJar(jarFile.toPath(), expectedDigests);
            }
        } catch (IOException | RuntimeException exception) {
            if (mode == Mode.WARN) {
                System.err.printf("Warning: Plugin integrity verification did not pass: %s%n", exception.getMessage());
                return;
            }
            throw exception;
        }
    }

    private JsonObject loadExpectedDigests() throws IOException {
        if (!Files.isRegularFile(manifestPath)) {
            throw new IOException("Plugin integrity manifest is required but was not found: " + manifestPath);
        }

        try (var reader = Files.newBufferedReader(manifestPath)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw new IOException("Plugin integrity manifest must be a JSON object");
            }

            JsonObject manifest = parsed.getAsJsonObject();
            int schemaVersion = manifest.has("schemaVersion")
                    ? manifest.get("schemaVersion").getAsInt()
                    : 0;
            String algorithm = manifest.has("algorithm")
                    ? manifest.get("algorithm").getAsString()
                    : "";
            JsonElement jars = manifest.get("jars");

            if (schemaVersion != 1 || !SHA_256.equalsIgnoreCase(algorithm)
                    || jars == null || !jars.isJsonObject()) {
                throw new IOException("Plugin integrity manifest has an unsupported schema or algorithm");
            }
            return jars.getAsJsonObject();
        } catch (IllegalStateException | NumberFormatException exception) {
            throw new IOException("Plugin integrity manifest is malformed", exception);
        }
    }

    private void verifyJar(Path jarPath, JsonObject expectedDigests) throws IOException {
        Path normalizedJar = jarPath.toAbsolutePath().normalize();
        if (!normalizedJar.startsWith(pluginRoot)) {
            throw new IOException("Plugin JAR is outside the configured plugin root: " + normalizedJar);
        }

        String relativePath = pluginRoot.relativize(normalizedJar).toString().replace('\\', '/');
        JsonElement expectedElement = expectedDigests.get(relativePath);
        if (expectedElement == null || !expectedElement.isJsonPrimitive()) {
            throw new IOException("Plugin JAR is not present in the integrity manifest: " + relativePath);
        }

        String expected = expectedElement.getAsString().trim().toLowerCase(Locale.ROOT);
        if (!expected.matches("[0-9a-f]{64}")) {
            throw new IOException("Plugin JAR digest is not valid SHA-256 hex: " + relativePath);
        }

        byte[] expectedBytes = HexFormat.of().parseHex(expected);
        byte[] actualBytes = HexFormat.of().parseHex(sha256(normalizedJar));
        if (!MessageDigest.isEqual(expectedBytes, actualBytes)) {
            throw new IOException("Plugin JAR digest does not match the integrity manifest: " + relativePath);
        }
    }

    private String sha256(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(SHA_256);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }

        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    enum Mode {
        REQUIRED,
        WARN,
        OFF;

        static Mode parse(String configured) {
            if (configured == null || configured.isBlank()) {
                return REQUIRED;
            }
            try {
                return valueOf(configured.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        INTEGRITY_MODE_CONFIG + " must be one of required, warn, or off");
            }
        }
    }
}
