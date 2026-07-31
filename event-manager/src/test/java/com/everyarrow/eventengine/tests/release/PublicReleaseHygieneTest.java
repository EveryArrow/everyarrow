/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 */

package com.everyarrow.eventengine.tests.release;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicReleaseHygieneTest {

    @Test
    void businessSourceLicensesRequireCommercialLicenseForProduction() throws IOException {
        Path repositoryRoot = findRepositoryRoot();
        String rootLicense = Files.readString(repositoryRoot.resolve("LICENSE"));
        String eventLicense = Files.readString(repositoryRoot.resolve("event-manager/LICENSE"));
        String dittoeLicense = Files.readString(repositoryRoot.resolve("dittoe-engine/LICENSE"));

        for (String license : List.of(rootLicense, eventLicense, dittoeLicense)) {
            String normalizedLicense = license.replaceAll("\\s+", " ");

            assertTrue(license.startsWith("Business Source License 1.1"));
            assertTrue(license.contains("Licensor:             EveryArrow LLC"));
            assertTrue(license.contains("Change Date:          2030-07-30"));
            assertTrue(license.contains("Change License:       Apache License, Version 2.0"));
            assertTrue(license.contains("Additional Use Grant: None"));
            assertTrue(normalizedLicense.contains("make non-production use of the Licensed Work"));
            assertTrue(normalizedLicense.contains("must purchase a commercial license"));
            assertFalse(normalizedLicense.contains("solely for your own Internal Business Purposes"));
            assertFalse(license.contains("EveryArrow, Inc."));
            assertFalse(license.contains("AndyBlum.io LLC"));
            assertFalse(license.contains("Production Use Limitation"));
        }

        String canonicalTermsMarker = "License text copyright";
        assertEquals(rootLicense.substring(rootLicense.indexOf(canonicalTermsMarker)),
                eventLicense.substring(eventLicense.indexOf(canonicalTermsMarker)));
        assertEquals(rootLicense.substring(rootLicense.indexOf(canonicalTermsMarker)),
                dittoeLicense.substring(dittoeLicense.indexOf(canonicalTermsMarker)));

        String licenseMap = Files.readString(repositoryRoot.resolve("LICENSES.md"));
        assertTrue(licenseMap.contains("Future commercial distributions"));
        assertTrue(licenseMap.contains("`utilities/` | Apache License 2.0"));
        assertTrue(licenseMap.contains("`dittoe-schema/` | Apache License 2.0"));

        String eventNotice = Files.readString(repositoryRoot.resolve("event-manager/LICENSE-NOTICE.md"));
        assertFalse(eventNotice.contains("fees owed"));
        assertTrue(eventNotice.replaceAll("\\s+", " ")
                .contains("exclusive grant from Wildthought LLC"));
    }

    @Test
    void editorAndOperatingSystemMetadataAreNotPublished() throws IOException {
        assertFalse(containsRegularFile(Path.of(".idea")), "IDE metadata must not be published.");

        List<Path> finderMetadata;
        try (var entries = Files.walk(Path.of("."))) {
            finderMetadata = entries
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(".DS_Store"))
                    .toList();
        }
        assertTrue(finderMetadata.isEmpty(), "Operating-system metadata must not be published: " + finderMetadata);
    }

    private static boolean containsRegularFile(Path path) throws IOException {
        if (!Files.exists(path)) {
            return false;
        }
        try (var entries = Files.walk(path)) {
            return entries.anyMatch(Files::isRegularFile);
        }
    }

    private static Path findRepositoryRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("LICENSES.md"))
                    && Files.isDirectory(candidate.resolve("event-manager"))
                    && Files.isDirectory(candidate.resolve("dittoe-engine"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new AssertionError("Unable to locate the EveryArrow repository root.");
    }
}
