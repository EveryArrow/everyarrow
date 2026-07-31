/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.github;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Utility class for fetching and downloading release assets from GitHub.
 * Configurable per-request for organization, repo, and tag.
 */
public class GitHubAssetFetcher {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-fA-F]{64}");
    private static final Set<String> TRUSTED_DOWNLOAD_HOSTS = Set.of(
            "github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private static final Gson GSON = new GsonBuilder().create();

    private final String token;

    /**
     * Create a new fetcher with the specified GitHub token
     */
    public GitHubAssetFetcher(String token) {
        this.token = token;
    }

    /**
     * Asset info returned from GitHub API
     */
    public static class AssetInfo {
        private final String name;
        private final long id;
        private final String owner;
        private final String repo;

        public AssetInfo(String name, int id, String owner, String repo) {
            this(name, (long) id, owner, repo);
        }

        public AssetInfo(String name, long id, String owner, String repo) {
            this.name = name;
            this.id = id;
            this.owner = owner;
            this.repo = repo;
        }

        public String getName() { return name; }
        public int getId() { return Math.toIntExact(id); }
        public long getLongId() { return id; }
        public String getOwner() { return owner; }
        public String getRepo() { return repo; }
    }

    /**
     * Integrity and resource limits required for every downloaded asset.
     */
    public record AssetVerification(String expectedSha256, long maximumBytes) {
        public AssetVerification {
            if (expectedSha256 == null || !SHA_256.matcher(expectedSha256).matches()) {
                throw new IllegalArgumentException(
                        "Expected SHA-256 must contain exactly 64 hexadecimal characters");
            }
            if (maximumBytes <= 0) {
                throw new IllegalArgumentException("Maximum asset size must be positive");
            }
            expectedSha256 = expectedSha256.toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Fetch asset info for a specific tag from a GitHub repository
     * 
     * @param owner GitHub organization or user
     * @param repo Repository name
     * @param tag Release tag name
     * @return AssetInfo containing asset details, or null if not found
     */
    public AssetInfo fetchAssetInfo(String owner, String repo, String tag) throws IOException, InterruptedException {
        return fetchAssetInfo(owner, repo, tag, null);
    }

    /**
     * Fetch asset info for a specific tag from a GitHub repository with optional asset name filter
     * 
     * @param owner GitHub organization or user
     * @param repo Repository name
     * @param tag Release tag name
     * @param assetNameFilter Optional asset name to filter by (null to get first asset)
     * @return AssetInfo containing asset details, or null if not found
     */
    public AssetInfo fetchAssetInfo(String owner, String repo, String tag, String assetNameFilter) throws IOException, InterruptedException {
        URI releaseUri = URI.create(
                "https://api.github.com/repos/"
                        + pathSegment(owner, "owner") + "/"
                        + pathSegment(repo, "repository") + "/releases/tags/"
                        + pathSegment(tag, "tag"));

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(releaseUri)
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET();
        addAuthorization(requestBuilder);

        HttpResponse<String> response = HTTP.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            System.err.println(
                    "[GitHub] Release lookup failed with status " + response.statusCode());
            return null;
        }

        JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
        JsonArray assets = json.getAsJsonArray("assets");

        if (assets == null || assets.isEmpty()) {
            System.err.println("[GitHub] No assets found for tag: " + tag);
            return null;
        }

        JsonObject asset = null;
        
        if (assetNameFilter == null || assetNameFilter.isEmpty()) {
            asset = assets.get(0).getAsJsonObject();
        } else {
            for (int i = 0; i < assets.size(); i++) {
                JsonObject currentAsset = assets.get(i).getAsJsonObject();
                String currentName = currentAsset.get("name").getAsString();
                if (currentName.equals(assetNameFilter)) {
                    asset = currentAsset;
                    break;
                }
            }
            
            if (asset == null) {
                System.err.println("[GitHub] Asset with name '" + assetNameFilter + "' not found for tag: " + tag);
                return null;
            }
        }

        String assetName = asset.get("name").getAsString();
        long assetId = asset.get("id").getAsLong();

        return new AssetInfo(assetName, assetId, owner, repo);
    }

    /**
     * Download a release asset to the specified destination
     * 
     * @param owner GitHub organization or user
     * @param repo Repository name
     * @param assetId Asset ID from GitHub API
     * @param destination Path to save the downloaded file
     */
    @Deprecated(forRemoval = true)
    public void downloadAsset(String owner, String repo, int assetId, Path destination)
            throws IOException, InterruptedException {
        throw verificationRequired();
    }

    /**
     * Download and verify a release asset before publishing it at the destination.
     */
    public void downloadAsset(
            String owner,
            String repo,
            long assetId,
            Path destination,
            AssetVerification verification) throws IOException, InterruptedException {
        if (assetId <= 0) {
            throw new IllegalArgumentException("GitHub asset ID must be positive");
        }
        if (destination == null) {
            throw new IllegalArgumentException("Destination is required");
        }
        if (verification == null) {
            throw verificationRequired();
        }

        URI assetUri = URI.create(
                "https://api.github.com/repos/"
                        + pathSegment(owner, "owner") + "/"
                        + pathSegment(repo, "repository") + "/releases/assets/"
                        + assetId);
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(assetUri)
                .timeout(Duration.ofMinutes(5))
                .header("Accept", "application/octet-stream")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET();
        addAuthorization(requestBuilder);

        HttpResponse<InputStream> apiResponse = HTTP.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream apiBody = apiResponse.body()) {
            if (isRedirect(apiResponse.statusCode())) {
                URI redirect = assetUri.resolve(apiResponse.headers().firstValue("Location")
                        .orElseThrow(() ->
                                new IOException("GitHub asset redirect is missing Location")));
                requireTrustedDownloadUri(redirect);
                HttpRequest downloadRequest = HttpRequest.newBuilder()
                    .uri(redirect)
                    .timeout(Duration.ofMinutes(5))
                    .GET()
                    .build();
                HttpResponse<InputStream> downloadResponse = HTTP.send(
                        downloadRequest,
                        HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream downloadBody = downloadResponse.body()) {
                    requireSuccess(downloadResponse.statusCode());
                    copyVerified(
                            downloadBody,
                            downloadResponse.headers().firstValueAsLong("Content-Length"),
                            destination,
                            verification);
                }
            } else {
                requireSuccess(apiResponse.statusCode());
                copyVerified(
                        apiBody,
                        apiResponse.headers().firstValueAsLong("Content-Length"),
                        destination,
                        verification);
            }
        }

        long fileSize = Files.size(destination);
        System.out.println("[GitHub] Downloaded " + fileSize + " bytes to: " + destination.getFileName());
    }

    /**
     * Convenience method to fetch asset info and download in one call
     * 
     * @param owner GitHub organization or user
     * @param repo Repository name
     * @param tag Release tag name
     * @param destination Path to save the downloaded file
     * @return AssetInfo if successful, null if failed
     */
    @Deprecated(forRemoval = true)
    public AssetInfo fetchAndDownload(String owner, String repo, String tag, Path destination)
            throws IOException, InterruptedException {
        throw verificationRequired();
    }

    /**
     * Convenience method to fetch asset info and download in one call with optional asset name filter
     * 
     * @param owner GitHub organization or user
     * @param repo Repository name
     * @param tag Release tag name
     * @param assetNameFilter Optional asset name to filter by (null to get first asset)
     * @param destination Path to save the downloaded file
     * @return AssetInfo if successful, null if failed
     */
    @Deprecated(forRemoval = true)
    public AssetInfo fetchAndDownload(
            String owner,
            String repo,
            String tag,
            String assetNameFilter,
            Path destination) throws IOException, InterruptedException {
        throw verificationRequired();
    }

    /**
     * Fetch a named release asset and download it with mandatory verification.
     */
    public AssetInfo fetchAndDownload(
            String owner,
            String repo,
            String tag,
            Path destination,
            AssetVerification verification) throws IOException, InterruptedException {
        return fetchAndDownload(owner, repo, tag, null, destination, verification);
    }

    /**
     * Fetch a release asset and download it with mandatory verification.
     */
    public AssetInfo fetchAndDownload(
            String owner,
            String repo,
            String tag,
            String assetNameFilter,
            Path destination,
            AssetVerification verification) throws IOException, InterruptedException {
        AssetInfo assetInfo = fetchAssetInfo(owner, repo, tag, assetNameFilter);
        if (assetInfo == null) {
            return null;
        }

        System.out.println(
                "[GitHub] Asset: " + assetInfo.getName()
                        + " (ID: " + assetInfo.getLongId() + ")");
        downloadAsset(owner, repo, assetInfo.getLongId(), destination, verification);
        return assetInfo;
    }

    static void copyVerified(
            InputStream input,
            OptionalLong declaredLength,
            Path destination,
            AssetVerification verification) throws IOException {
        if (declaredLength.isPresent() && declaredLength.getAsLong() > verification.maximumBytes()) {
            throw new IOException(
                    "GitHub asset exceeds the configured maximum of "
                            + verification.maximumBytes() + " bytes");
        }

        Path target = destination.toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null) {
            throw new IOException("Destination must have a parent directory");
        }
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "." + target.getFileName() + "-", ".part");
        boolean published = false;
        try {
            MessageDigest digest = sha256Digest();
            long count = 0;
            byte[] buffer = new byte[8192];
            try (var output = Files.newOutputStream(
                    temporary,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    count += read;
                    if (count > verification.maximumBytes()) {
                        throw new IOException(
                                "GitHub asset exceeds the configured maximum of "
                                        + verification.maximumBytes() + " bytes");
                    }
                    digest.update(buffer, 0, read);
                    output.write(buffer, 0, read);
                }
            }

            byte[] expected = HexFormat.of().parseHex(verification.expectedSha256());
            if (!MessageDigest.isEqual(expected, digest.digest())) {
                throw new IOException("GitHub asset SHA-256 verification failed");
            }
            moveAtomically(temporary, target);
            published = true;
        } finally {
            if (!published) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    static boolean isTrustedDownloadUri(URI uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())
                || uri.getUserInfo() != null || uri.getHost() == null) {
            return false;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return TRUSTED_DOWNLOAD_HOSTS.contains(host)
                || host.endsWith(".githubusercontent.com");
    }

    private static void requireTrustedDownloadUri(URI uri) throws IOException {
        if (!isTrustedDownloadUri(uri)) {
            throw new IOException("GitHub asset redirect target is not trusted");
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void requireSuccess(int statusCode) throws IOException {
        if (statusCode != 200) {
            throw new IOException("GitHub asset download failed with status " + statusCode);
        }
    }

    private static boolean isRedirect(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 303
                || statusCode == 307 || statusCode == 308;
    }

    private void addAuthorization(HttpRequest.Builder requestBuilder) {
        if (token != null && !token.isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + token.trim());
        }
    }

    private static String pathSegment(String value, String description) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("GitHub " + description + " is required");
        }
        return URLEncoder.encode(value.trim(), StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private static IllegalArgumentException verificationRequired() {
        return new IllegalArgumentException(
                "GitHub asset downloads require expected SHA-256 and maximum byte count");
    }
}
