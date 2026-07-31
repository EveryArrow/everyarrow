package com.everyarrow.dittoe.cleanup;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Utility to delete temporary pipeline run directories.
 * These are directories with timestamp format (YYYY-MM-DD-HH-MM-SS).
 */
public class DeleteTempPipelineDirectories {
    
    // Pattern to match timestamp directories: YYYY-MM-DD-HH-MM-SS
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2}");
    
    public static void main(String[] args) throws IOException {
        String pipelinesDir = "src/test/resources/pipelines";
        
        System.out.println("Scanning for temporary pipeline run directories...");
        System.out.println("Base directory: " + pipelinesDir);
        System.out.println("=" .repeat(60));
        System.out.println();
        
        int deletedCount = 0;
        int failedCount = 0;
        List<String> failedDeletions = new ArrayList<>();
        
        Path basePath = Paths.get(pipelinesDir);
        
        if (!Files.exists(basePath)) {
            System.err.println("Error: Directory does not exist: " + pipelinesDir);
            System.exit(1);
        }
        
        // Iterate through all pipeline directories
        try (DirectoryStream<Path> pipelineDirs = Files.newDirectoryStream(basePath, Files::isDirectory)) {
            for (Path pipelineDir : pipelineDirs) {
                String pipelineName = pipelineDir.getFileName().toString();
                
                // Look for timestamp directories within each pipeline directory
                try (DirectoryStream<Path> entries = Files.newDirectoryStream(pipelineDir, Files::isDirectory)) {
                    for (Path entry : entries) {
                        String dirName = entry.getFileName().toString();
                        
                        // Check if directory name matches timestamp pattern
                        if (TIMESTAMP_PATTERN.matcher(dirName).matches()) {
                            try {
                                deleteDirectory(entry);
                                System.out.println("✓ Deleted: " + pipelineName + "/" + dirName);
                                deletedCount++;
                            } catch (IOException e) {
                                System.err.println("✗ Failed to delete: " + pipelineName + "/" + dirName);
                                System.err.println("  Error: " + e.getMessage());
                                failedCount++;
                                failedDeletions.add(pipelineName + "/" + dirName);
                            }
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Warning: Could not read directory: " + pipelineName);
                }
            }
        }
        
        // Print summary
        System.out.println();
        System.out.println("=" .repeat(60));
        System.out.println("Summary:");
        System.out.println("  Directories deleted: " + deletedCount);
        System.out.println("  Failed deletions: " + failedCount);
        
        if (!failedDeletions.isEmpty()) {
            System.out.println();
            System.out.println("Failed deletions:");
            failedDeletions.forEach(path -> System.out.println("  - " + path));
        }
        
        System.out.println("=" .repeat(60));
    }
    
    /**
     * Recursively delete a directory and all its contents.
     */
    private static void deleteDirectory(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
