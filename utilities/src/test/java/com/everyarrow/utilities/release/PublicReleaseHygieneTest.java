/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.release;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicReleaseHygieneTest {

    private static final Pattern USES_ACTION = Pattern.compile("(?m)^\\s*uses:\\s+([^\\s#]+)");
    private static final Pattern PINNED_ACTION_REF = Pattern.compile(".+@[0-9a-f]{40}$");

    @Test
    void releaseWorkflowsUsePinnedActionsAndProtectedTagReleases() throws Exception {
        for (Path workflow : releaseWorkflows()) {
            String text = Files.readString(workflow, StandardCharsets.UTF_8);

            assertFalse(text.contains("workflow_dispatch"), workflow + " must not allow manual release dispatch.");
            assertFalse(text.contains("github.event_name == 'workflow_dispatch'"),
                    workflow + " must not publish artifacts or releases from manual dispatch.");
            assertTrue(text.contains("git merge-base --is-ancestor \"$GITHUB_SHA\" \"origin/main\""),
                    workflow + " must verify release tags are reachable from main.");

            Matcher matcher = USES_ACTION.matcher(text);
            while (matcher.find()) {
                String action = matcher.group(1).replace("\"", "").replace("'", "");
                assertTrue(PINNED_ACTION_REF.matcher(action).matches(),
                        workflow + " contains mutable action reference: " + action);
            }
        }
    }

    @Test
    void heldBackProductSurfacesAreNotPublished() {
        for (Path heldBackPath : List.of(
                Path.of("everyarrow-bootstrap"),
                Path.of("everyarrow-provisioner"),
                Path.of("efs-log-tools"),
                Path.of("generated_functions"),
                Path.of("keycloak-email-whitelist"),
                Path.of("keycloak-enhanced-otp-form"),
                Path.of("keycloak-post-login-listener"),
                Path.of("src", "main", "java", "com", "everyarrow", "utilities", "internal"),
                Path.of("src", "main", "java", "com", "everyarrow", "utilities", "templates", "providers", "data" + "mover"),
                Path.of("src", "main", "java", "com", "everyarrow", "utilities", "templates", "providers", "leads", "everyarrow"),
                Path.of("src", "main", "java", "com", "everyarrow", "utilities", "tools", "efs"))) {
            assertFalse(containsRegularFile(heldBackPath), heldBackPath + " is private full-version code.");
        }
    }

    @Test
    void publicGovernanceFilesExist() {
        assertTrue(Files.isRegularFile(Path.of("SECURITY.md")), "SECURITY.md is required for public release.");
        assertTrue(Files.isRegularFile(Path.of(".github", "CODEOWNERS")), "CODEOWNERS is required for public release.");
        assertTrue(readUnchecked(Path.of(".github", "CODEOWNERS")).contains("@EveryArrow/maintainers"),
                "CODEOWNERS must identify a maintainer owner.");
    }

    @Test
    void publicLicenseFilesDeclareApacheTwo() {
        String license = readUnchecked(Path.of("LICENSE"));
        String notice = readUnchecked(Path.of("NOTICE"));
        String readme = readUnchecked(Path.of("README.md"));
        String pom = readUnchecked(Path.of("pom.xml"));

        assertTrue(license.startsWith("Apache License"), "LICENSE must contain the Apache License text.");
        assertTrue(notice.contains("EveryArrow Utilities"), "NOTICE must identify the licensed work.");
        assertTrue(readme.contains("Apache License, Version 2.0"), "README must describe the public license.");
        assertTrue(pom.contains("<name>Apache License, Version 2.0</name>"), "pom.xml must publish Apache license metadata.");

        String oldAvailabilityTerm = "source" + "-available";
        String oldLicenseName = "Business" + " Source License";

        assertFalse(readme.contains(oldAvailabilityTerm), "README must describe this repository as open source.");
        assertFalse(pom.contains(oldLicenseName), "pom.xml must not publish old license metadata.");
    }

    private static List<Path> releaseWorkflows() {
        return List.of(
                Path.of(".github", "workflows", "release.yml")
        );
    }

    private static boolean containsRegularFile(Path path) {
        if (!Files.exists(path)) {
            return false;
        }
        try (var entries = Files.walk(path)) {
            return entries.anyMatch(Files::isRegularFile);
        } catch (IOException e) {
            throw new AssertionError("Failed to inspect " + path, e);
        }
    }

    private static String readUnchecked(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("Failed to read " + path, e);
        }
    }
}
