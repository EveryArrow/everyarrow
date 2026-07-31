package com.everyarrow.eventengine.core.services.plugins;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PluginIntegrityVerifierTest {

    @TempDir
    Path pluginRoot;

    @Test
    void acceptsJarWhoseDigestMatchesManifest() throws Exception {
        Path jar = writeJar("console/console.jar", "trusted-content");
        Path manifest = writeManifest("console/console.jar", sha256(jar));

        PluginIntegrityVerifier verifier = requiredVerifier(manifest);

        assertDoesNotThrow(() -> verifier.verify(List.of(jar.toFile())));
    }

    @Test
    void rejectsJarWhoseDigestDoesNotMatchManifest() throws Exception {
        Path jar = writeJar("console/console.jar", "tampered-content");
        Path manifest = writeManifest("console/console.jar", "0".repeat(64));

        PluginIntegrityVerifier verifier = requiredVerifier(manifest);

        assertThrows(java.io.IOException.class, () -> verifier.verify(List.of(jar.toFile())));
    }

    @Test
    void rejectsJarMissingFromManifest() throws Exception {
        Path jar = writeJar("console/console.jar", "content");
        Path manifest = writeManifest("other/other.jar", sha256(jar));

        PluginIntegrityVerifier verifier = requiredVerifier(manifest);

        assertThrows(java.io.IOException.class, () -> verifier.verify(List.of(jar.toFile())));
    }

    @Test
    void requiredModeRejectsMissingManifest() throws Exception {
        Path jar = writeJar("console/console.jar", "content");
        PluginIntegrityVerifier verifier = requiredVerifier(pluginRoot.resolve("missing.json"));

        assertThrows(java.io.IOException.class, () -> verifier.verify(List.of(jar.toFile())));
    }

    private PluginIntegrityVerifier requiredVerifier(Path manifest) {
        return new PluginIntegrityVerifier(
                pluginRoot,
                manifest,
                PluginIntegrityVerifier.Mode.REQUIRED);
    }

    private Path writeJar(String relativePath, String content) throws Exception {
        Path jar = pluginRoot.resolve(relativePath);
        Files.createDirectories(jar.getParent());
        Files.writeString(jar, content);
        return jar;
    }

    private Path writeManifest(String relativePath, String digest) throws Exception {
        JsonObject jars = new JsonObject();
        jars.addProperty(relativePath, digest);

        JsonObject manifest = new JsonObject();
        manifest.addProperty("schemaVersion", 1);
        manifest.addProperty("algorithm", "SHA-256");
        manifest.add("jars", jars);

        Path path = pluginRoot.resolve("plugin-integrity.json");
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(manifest));
        return path;
    }

    private String sha256(Path file) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
        return HexFormat.of().formatHex(digest);
    }
}
