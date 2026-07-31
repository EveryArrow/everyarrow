/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.github;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubAssetFetcherTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void publishesOnlyAnAssetWithTheExpectedDigest() throws Exception {
        byte[] content = "reviewed release".getBytes(StandardCharsets.UTF_8);
        Path destination = temporaryDirectory.resolve("release.jar");

        GitHubAssetFetcher.copyVerified(
                new ByteArrayInputStream(content),
                OptionalLong.of(content.length),
                destination,
                new GitHubAssetFetcher.AssetVerification(sha256(content), content.length));

        assertEquals("reviewed release", Files.readString(destination));
    }

    @Test
    void preservesExistingDestinationWhenDigestDoesNotMatch() throws Exception {
        Path destination = temporaryDirectory.resolve("release.jar");
        Files.writeString(destination, "known-good");
        byte[] replacement = "substituted".getBytes(StandardCharsets.UTF_8);

        assertThrows(
                java.io.IOException.class,
                () -> GitHubAssetFetcher.copyVerified(
                        new ByteArrayInputStream(replacement),
                        OptionalLong.of(replacement.length),
                        destination,
                        new GitHubAssetFetcher.AssetVerification("0".repeat(64), 100)));

        assertEquals("known-good", Files.readString(destination));
    }

    @Test
    void rejectsOversizedAssetsBeforePublishing() {
        byte[] content = "too large".getBytes(StandardCharsets.UTF_8);
        Path destination = temporaryDirectory.resolve("release.jar");

        assertThrows(
                java.io.IOException.class,
                () -> GitHubAssetFetcher.copyVerified(
                        new ByteArrayInputStream(content),
                        OptionalLong.empty(),
                        destination,
                        new GitHubAssetFetcher.AssetVerification(sha256(content), 3)));

        assertFalse(Files.exists(destination));
    }

    @Test
    void acceptsOnlyHttpsGitHubOwnedRedirects() {
        assertTrue(GitHubAssetFetcher.isTrustedDownloadUri(
                URI.create("https://release-assets.githubusercontent.com/file")));
        assertTrue(GitHubAssetFetcher.isTrustedDownloadUri(
                URI.create("https://tenant.objects.githubusercontent.com/file")));
        assertFalse(GitHubAssetFetcher.isTrustedDownloadUri(
                URI.create("http://release-assets.githubusercontent.com/file")));
        assertFalse(GitHubAssetFetcher.isTrustedDownloadUri(
                URI.create("https://githubusercontent.com.attacker.example/file")));
        assertFalse(GitHubAssetFetcher.isTrustedDownloadUri(
                URI.create("https://user@objects.githubusercontent.com/file")));
    }

    @Test
    void rejectsInvalidVerificationContracts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new GitHubAssetFetcher.AssetVerification("not-a-digest", 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new GitHubAssetFetcher.AssetVerification("0".repeat(64), 0));
    }

    @Test
    void preservesLegacyAssetIdAccessWhileSupportingLongIds() {
        GitHubAssetFetcher.AssetInfo legacy =
                new GitHubAssetFetcher.AssetInfo("asset", 42, "owner", "repo");
        assertEquals(42, legacy.getId());
        assertEquals(42L, legacy.getLongId());

        GitHubAssetFetcher.AssetInfo large =
                new GitHubAssetFetcher.AssetInfo(
                        "asset",
                        (long) Integer.MAX_VALUE + 1,
                        "owner",
                        "repo");
        assertEquals((long) Integer.MAX_VALUE + 1, large.getLongId());
        assertThrows(ArithmeticException.class, large::getId);
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
