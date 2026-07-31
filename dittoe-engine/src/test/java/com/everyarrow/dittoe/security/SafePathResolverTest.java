package com.everyarrow.dittoe.security;

import com.everyarrow.dittoe.utility.security.SafePathResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafePathResolverTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesRelativePathInsideBase() {
        Path base = Path.of("target", "safe-path-test");

        Path resolved = SafePathResolver.resolveUnderBase(base, "nested", "file.csv");

        assertTrue(resolved.endsWith(Path.of("target", "safe-path-test", "nested", "file.csv")));
    }

    @Test
    void rejectsTraversalOutsideBase() {
        assertThrows(IllegalArgumentException.class,
                () -> SafePathResolver.resolveUnderBase(Path.of("target", "safe-path-test"), "..", "outside.csv"));
    }

    @Test
    void rejectsAbsoluteUserComponent() {
        Path absoluteComponent = Path.of(System.getProperty("user.dir")).toAbsolutePath();

        assertThrows(IllegalArgumentException.class,
                () -> SafePathResolver.resolveUnderBase(Path.of("target", "safe-path-test"), absoluteComponent.toString()));
    }

    @Test
    void preservesFileName() {
        Path resolved = SafePathResolver.resolveUnderBase(Path.of("target"), "safe.txt");

        assertEquals("safe.txt", resolved.getFileName().toString());
    }

    @Test
    void rejectsConfiguredBaseOutsideAllowedRoots() {
        assertThrows(IllegalArgumentException.class,
                () -> SafePathResolver.resolveUnderBase(Path.of("..", "outside-workspace"), "file.csv"));
    }

    @Test
    void rejectsSymlinkThatEscapesConfiguredBase() throws Exception {
        Path base = tempDir.resolve("base");
        Path outside = tempDir.resolve("outside");
        Files.createDirectories(base);
        Files.createDirectories(outside);
        createDirectoryLink(base.resolve("link"), outside);

        assertThrows(IllegalArgumentException.class,
                () -> SafePathResolver.resolveUnderBase(base, "link", "file.csv"));
    }

    @Test
    void rejectsUnsafeRemoteStagingNames() {
        assertThrows(IllegalArgumentException.class,
                () -> SafePathResolver.requireSafeRelativePath("../escape.csv", "remote file"));
    }

    @Test
    void createsRuntimeTempFilesUnderControlledRoot() throws Exception {
        Path tempFile = SafePathResolver.createTempFile("unit", ".tmp");

        try {
            assertTrue(tempFile.startsWith(SafePathResolver.resolveRuntimeTempDirectory()));
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private static void createDirectoryLink(Path link, Path target) throws Exception {
        if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows")) {
            Files.createSymbolicLink(link, target);
            return;
        }

        Process process = new ProcessBuilder(
                "cmd.exe",
                "/d",
                "/c",
                "mklink",
                "/J",
                link.toString(),
                target.toString())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
    }
}
