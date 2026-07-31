/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.compression;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipUtilTest {

    @TempDir
    Path tempDir;

    @Test
    void selectedEntriesAreExtractedEvenWhenEarlierEntriesAreNotSelected() throws Exception {
        Path zip = tempDir.resolve("selected.zip");
        createZip(zip,
                entry("ignore.txt", "ignore"),
                entry("nested/keep.txt", "keep"));

        List<File> extracted = ZipUtil.unzip(zip.toFile(), Set.of("nested/keep.txt"));

        assertEquals(1, extracted.size());
        assertTrue(Files.exists(tempDir.resolve("selected").resolve("nested").resolve("keep.txt")));
        assertFalse(Files.exists(tempDir.resolve("selected").resolve("ignore.txt")));
    }

    @Test
    void nullSelectionExtractsAllEntries() throws Exception {
        Path zip = tempDir.resolve("all.zip");
        createZip(zip,
                entry("first.txt", "first"),
                entry("second.txt", "second"));

        List<File> extracted = ZipUtil.unzip(zip.toFile(), null);

        assertEquals(2, extracted.size());
        assertTrue(Files.exists(tempDir.resolve("all").resolve("first.txt")));
        assertTrue(Files.exists(tempDir.resolve("all").resolve("second.txt")));
    }

    @Test
    void rejectsZipSlipEntries() throws Exception {
        Path zip = tempDir.resolve("slip.zip");
        createZip(zip, entry("../outside.txt", "escape"));

        assertThrows(IOException.class, () -> ZipUtil.unzip(zip.toFile(), Set.of("../outside.txt")));
        assertFalse(Files.exists(tempDir.resolve("outside.txt")));
    }

    @Test
    void rejectsArchivesThatExceedTheEntryLimit() throws Exception {
        Path zip = tempDir.resolve("entries.zip");
        createZip(zip, entry("first.txt", "first"), entry("second.txt", "second"));

        IOException error = assertThrows(
                IOException.class,
                () -> ZipUtil.unzip(zip.toFile(), null, 1, 1024));

        assertTrue(error.getMessage().contains("entry count"));
    }

    @Test
    void rejectsArchivesThatExceedTheUncompressedSizeLimit() throws Exception {
        Path zip = tempDir.resolve("size.zip");
        createZip(zip, entry("payload.txt", "too-large"));

        IOException error = assertThrows(
                IOException.class,
                () -> ZipUtil.unzip(zip.toFile(), null, 10, 4));

        assertTrue(error.getMessage().contains("uncompressed size"));
    }

    private static TestZipEntry entry(String name, String value) {
        return new TestZipEntry(name, value);
    }

    private static void createZip(Path path, TestZipEntry... entries) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(path))) {
            for (TestZipEntry entry : entries) {
                out.putNextEntry(new ZipEntry(entry.name));
                out.write(entry.value.getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
    }

    private record TestZipEntry(String name, String value) {
    }
}
