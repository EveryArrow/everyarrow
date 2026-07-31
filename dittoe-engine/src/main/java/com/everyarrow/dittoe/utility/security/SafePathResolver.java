package com.everyarrow.dittoe.utility.security;

import com.everyarrow.dittoe.utility.Config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SafePathResolver {
    private static final String ALLOWED_ROOTS_ENV = "DITTOE_ALLOWED_FILE_ROOTS";
    private static final String TEMP_DIR_ENV = "DITTOE_TEMP_DIR";

    private SafePathResolver() {
    }

    public static Path resolvePipelinePath(String pipelineName, String... components) {
        return resolveUnderBase(Path.of(Config.BASE_DIR), prepend(pipelineName, components));
    }

    public static Path resolveUnderBase(String basePath, String... components) {
        return resolveUnderBase(Path.of(requireValue(basePath, "basePath")), components);
    }

    public static Path resolveUnderBase(Path basePath, String... components) {
        Path base = requireAllowedPath(basePath, "base path");
        Path resolved = base;

        for (String component : components) {
            String value = requireValue(component, "path component");
            Path componentPath = Path.of(value);
            if (componentPath.isAbsolute()) {
                throw new IllegalArgumentException("Absolute path components are not allowed: " + value);
            }
            resolved = resolved.resolve(componentPath).normalize();
        }

        if (!resolved.startsWith(base)) {
            throw new IllegalArgumentException("Resolved path escapes its configured base directory");
        }

        requirePhysicalContainment(base, resolved);
        requireAllowedPath(resolved, "resolved path");
        return resolved;
    }

    public static Path resolveAllowedPath(String path, String name) {
        return requireAllowedPath(Path.of(requireValue(path, name)), name);
    }

    public static Path resolveRuntimeTempDirectory() {
        String configuredTempDir = System.getenv().getOrDefault(TEMP_DIR_ENV,
                Path.of(Config.BASE_DIR, "tmp").toString());
        return resolveAllowedPath(configuredTempDir, "runtime temp directory");
    }

    public static Path createTempFile(String prefix, String suffix) throws IOException {
        Path tempDirectory = resolveRuntimeTempDirectory();
        Files.createDirectories(tempDirectory);
        return Files.createTempFile(tempDirectory, sanitizeTempPrefix(prefix), suffix);
    }

    public static String requireSafeRelativePath(String value, String name) {
        String path = requireValue(value, name);
        Path relativePath = Path.of(path);
        if (relativePath.isAbsolute()) {
            throw new IllegalArgumentException(name + " cannot be an absolute path");
        }
        Path normalized = relativePath.normalize();
        if (normalized.startsWith("..")) {
            throw new IllegalArgumentException(name + " cannot escape its configured root");
        }
        return normalized.toString().replace(File.separatorChar, '/');
    }

    public static String safeFileName(String value, String fallback) {
        String candidate = value == null ? "" : value.trim();
        if (candidate.isBlank()) {
            candidate = fallback;
        }
        String safe = candidate.replaceAll("[^A-Za-z0-9._-]", "_");
        safe = safe.replaceAll("_+", "_");
        if (safe.isBlank() || safe.equals(".") || safe.equals("..")) {
            return fallback;
        }
        return safe;
    }

    private static String[] prepend(String first, String[] rest) {
        String[] values = new String[rest.length + 1];
        values[0] = first;
        System.arraycopy(rest, 0, values, 1, rest.length);
        return values;
    }

    private static Path requireAllowedPath(Path path, String name) {
        Path absolutePath = path.toAbsolutePath().normalize();
        if (allowedRoots().stream().noneMatch(root -> isContainedByRoot(root, absolutePath))) {
            throw new IllegalArgumentException(name + " is outside the configured DITTOE file roots: " + absolutePath);
        }
        return absolutePath;
    }

    private static boolean isContainedByRoot(Path root, Path candidate) {
        if (!candidate.startsWith(root)) {
            return false;
        }
        try {
            return resolvePhysicalPath(candidate).startsWith(resolvePhysicalPath(root));
        } catch (IOException e) {
            return false;
        }
    }

    private static void requirePhysicalContainment(Path base, Path candidate) {
        try {
            Path physicalBase = resolvePhysicalPath(base);
            Path physicalCandidate = resolvePhysicalPath(candidate);
            if (!physicalCandidate.startsWith(physicalBase)) {
                throw new IllegalArgumentException("Resolved path escapes its configured base directory through a link");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not verify resolved path containment", e);
        }
    }

    private static Path resolvePhysicalPath(Path path) throws IOException {
        Path absolutePath = path.toAbsolutePath().normalize();
        Path existingAncestor = absolutePath;
        while (existingAncestor != null && !Files.exists(existingAncestor, LinkOption.NOFOLLOW_LINKS)) {
            existingAncestor = existingAncestor.getParent();
        }
        if (existingAncestor == null) {
            throw new IOException("Path has no existing ancestor: " + absolutePath);
        }

        Path physicalAncestor = existingAncestor.toRealPath();
        return physicalAncestor.resolve(existingAncestor.relativize(absolutePath)).normalize();
    }

    private static List<Path> allowedRoots() {
        List<Path> roots = new ArrayList<>();
        roots.add(Path.of(System.getProperty("user.dir")));
        roots.add(Path.of(Config.BASE_DIR));
        roots.add(Path.of(Config.LOG_ROOT));
        roots.add(Path.of(System.getProperty("java.io.tmpdir")));

        String configuredRoots = System.getenv(ALLOWED_ROOTS_ENV);
        if (configuredRoots != null && !configuredRoots.isBlank()) {
            for (String root : configuredRoots.split(File.pathSeparator)) {
                if (!root.isBlank()) {
                    roots.add(Path.of(root));
                }
            }
        }

        return roots.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .distinct()
                .toList();
    }

    private static String requireValue(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return value;
    }

    private static String sanitizeTempPrefix(String prefix) {
        String safe = safeFileName(prefix, "dittoe").toLowerCase(Locale.ROOT);
        if (safe.length() < 3) {
            return (safe + "dittoe").substring(0, 3);
        }
        return safe;
    }
}
