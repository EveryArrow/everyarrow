package com.everyarrow.dittoe.security;

import com.everyarrow.dittoe.pipelines.behaviors.compression.ZipUtil;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ZipUtilSecurityTest {
    @Test
    void extractsSelectedFileAfterSkippingEarlierEntry() throws IOException {
        Path zip = Files.createTempFile("dittoe-zip-select", ".zip");
        writeZip(zip, MapEntry.of("skip.txt", "skip"), MapEntry.of("keep.txt", "keep"));

        List<File> extracted = ZipUtil.unzip(zip.toFile(), Set.of("keep.txt"));

        assertEquals(1, extracted.size());
        assertEquals("keep.txt", extracted.get(0).getName());
    }

    @Test
    void rejectsZipSlipEntry() throws IOException {
        Path zip = Files.createTempFile("dittoe-zip-slip", ".zip");
        writeZip(zip, MapEntry.of("../escape.txt", "bad"));

        assertThrows(IOException.class, () -> ZipUtil.unzip(zip.toFile(), Set.of("../escape.txt")));
    }

    private static void writeZip(Path zip, MapEntry... entries) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (MapEntry entry : entries) {
                output.putNextEntry(new ZipEntry(entry.name()));
                output.write(entry.value().getBytes());
                output.closeEntry();
            }
        }
    }

    private record MapEntry(String name, String value) {
        static MapEntry of(String name, String value) {
            return new MapEntry(name, value);
        }
    }
}
