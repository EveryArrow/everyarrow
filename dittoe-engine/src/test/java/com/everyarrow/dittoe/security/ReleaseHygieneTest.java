package com.everyarrow.dittoe.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseHygieneTest {
    private static final Pattern TAG_PINNED_ACTION = Pattern.compile(
            "^\\s*uses:\\s+[^\\s]+@(v\\d+|main|master)\\s*$",
            Pattern.CASE_INSENSITIVE);

    private final Path repoRoot = Path.of(System.getProperty("user.dir"));

    @Test
    void dockerExamplesDoNotPublishWeakDefaultPasswords() throws IOException {
        List<Path> files = List.of(
                repoRoot.resolve("docker/.env.example"),
                repoRoot.resolve("docker/docker-compose.yml"),
                repoRoot.resolve("docker/README.md"));

        for (Path file : files) {
            String content = Files.readString(file);
            assertFalse(content.contains("dittoe123"), file + " must not publish a reusable sample password.");
            assertFalse(content.contains("root123"), file + " must not publish a reusable root password.");
            assertFalse(content.contains("POSTGRES_PASSWORD:-"), file + " must require an explicit local password.");
            assertFalse(content.contains("MYSQL_ROOT_PASSWORD:-"), file + " must require an explicit local root password.");
            assertFalse(content.contains("MYSQL_PASSWORD:-"), file + " must require an explicit local password.");
        }
    }

    @Test
    void githubActionsArePinnedToImmutableReferences() throws IOException {
        List<Path> workflows = List.of(
                repoRoot.resolve(".github/workflows/tests.yaml"),
                repoRoot.resolve(".github/workflows/build-and-release.yaml"));

        for (Path workflow : workflows) {
            for (String line : Files.readAllLines(workflow)) {
                assertFalse(TAG_PINNED_ACTION.matcher(line).matches(),
                        workflow + " must pin third-party actions to immutable SHAs: " + line.trim());
                assertFalse(line.contains("SecurityReview"),
                        workflow + " must not publish temporary review branch triggers.");
            }
        }
    }

    @Test
    void sourcePackagesDoNotContainRunPromptFiles() throws IOException {
        try (Stream<Path> paths = Files.walk(repoRoot.resolve("src/main/java"))) {
            List<Path> promptFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals("NEXT_RUN_PROMPT.md"))
                    .toList();

            assertTrue(promptFiles.isEmpty(),
                    "Public source packages must not contain Codex next-run prompts: " + promptFiles);
        }
    }

    @Test
    void licenseRequiresCommercialLicenseForProduction() throws IOException {
        String license = Files.readString(repoRoot.resolve("LICENSE"));
        String normalizedLicense = license.replaceAll("\\s+", " ");

        assertTrue(license.startsWith("Business Source License 1.1"));
        assertTrue(license.contains("Licensor:             EveryArrow LLC"));
        assertTrue(license.contains("Change Date:          2030-07-30"));
        assertTrue(license.contains("Additional Use Grant: None"));
        assertTrue(normalizedLicense.contains("make non-production use of the Licensed Work"));
        assertTrue(normalizedLicense.contains("must purchase a commercial license"));
        assertFalse(normalizedLicense.contains("solely for your own Internal Business Purposes"));
        assertFalse(license.contains("Production Use Limitation"));
        assertFalse(license.contains("EveryArrow, Inc."));
    }
}
