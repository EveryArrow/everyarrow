package com.everyarrow.dittoe.pipelines.behaviors.compression;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.Set;

public class ZipUtil {
    private static final int BUFFER_SIZE = 8192;
    private static final int MAX_ENTRIES = 10_000;
    private static final long MAX_UNCOMPRESSED_BYTES = 1_073_741_824L;

    /**
     * Unzips the given ZIP file into a destination directory and returns the list of extracted files.
     *
     * @param zipFile the ZIP file to extract
     * @return list of extracted File objects (only files, not directories)
     * @throws IOException if an I/O error occurs during extraction
     */
    public static List<File> unzip(File zipFile, Set<String> selectiveFilesToUnzip) throws IOException {
        // Create a destination directory using the ZIP file name (without extension)
        File destDir = new File(zipFile.getParent(), removeExtension(zipFile.getName()));
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw new IOException("Could not create destination directory: " + destDir);
        }
        
        List<File> extractedFiles = new ArrayList<>();

        // Try-with-resources to ensure streams are closed
        try (InputStream fis = new FileInputStream(zipFile);
             ZipInputStream zis = new ZipInputStream(fis)) {

            ZipEntry entry;
            int entryCount = 0;
            long totalUncompressedBytes = 0;
            while ((entry = zis.getNextEntry()) != null) {
                if (++entryCount > MAX_ENTRIES) {
                    throw new IOException("ZIP archive exceeds the maximum supported entry count.");
                }
                if (selectiveFilesToUnzip != null && !selectiveFilesToUnzip.isEmpty()
                        && !selectiveFilesToUnzip.contains(entry.getName())) {
                    zis.closeEntry();
                    continue;
                }
                // Create a safe file reference for this entry
                File newFile = createFile(destDir, entry);

                if (entry.isDirectory()) {
                    // If the entry is a directory, create it (if needed) and continue.
                    if (!newFile.exists() && !newFile.mkdirs()) {
                        throw new IOException("Failed to create directory: " + newFile);
                    }
                } else {
                    // Ensure parent directories exist
                    File parent = newFile.getParentFile();
                    if (!parent.exists() && !parent.mkdirs()) {
                        throw new IOException("Failed to create directory: " + parent);
                    }

                    // Write the file contents
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            totalUncompressedBytes += len;
                            if (totalUncompressedBytes > MAX_UNCOMPRESSED_BYTES) {
                                throw new IOException("ZIP archive exceeds the maximum supported uncompressed size.");
                            }
                            fos.write(buffer, 0, len);
                        }
                    }
                    // Add the file to our list of extracted files
                    extractedFiles.add(newFile);
                }
                zis.closeEntry();
            }
        }
        return extractedFiles;
    }

    /**
     * Creates a new File for the given ZIP entry inside the destination directory.
     * This method ensures that the resulting file path is secure (i.e., avoids Zip Slip).
     *
     * @param destinationDir the destination directory where files should be extracted
     * @param zipEntry the current ZIP entry
     * @return the new File object
     * @throws IOException if the file path is invalid or outside the destination directory
     */
    private static File createFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());
        
        // Prevent Zip Slip by checking canonical paths
        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();
        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside the target dir: " + zipEntry.getName());
        }
        return destFile;
    }

    /**
     * Removes the extension from a filename.
     *
     * @param filename the filename to process
     * @return filename without its extension
     */
    private static String removeExtension(String filename) {
        int pos = filename.lastIndexOf(".");
        if (pos > 0) {
            return filename.substring(0, pos);
        }
        return filename;
    }
}
