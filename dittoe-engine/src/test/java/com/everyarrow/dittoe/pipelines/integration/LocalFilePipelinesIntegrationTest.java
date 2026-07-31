package com.everyarrow.dittoe.pipelines.integration;

import com.everyarrow.dittoe.pipelines.execution.Driver;
import com.everyarrow.dittoe.utility.Config;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for all pipelines.
 * Tests the complete pipeline execution from source CSV to target CSV.
 */
public class LocalFilePipelinesIntegrationTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    public void setUp() {
        // Set the BASE_DIR to use test resources
        Config.BASE_DIR = "src/test/resources/pipelines";
        
        // Use in-memory instrumentation for tests
        Config.INSTRUMENTATION_SERVICE_TYPE = "inmemory";

        // Set the pipeline metadata file name
        Config.PIPELINE_METADATA_FILE = "local-file-csv-to-local-file-csv-pmd.json";
    }

    @Test
    public void testAllPipelinesExecution() throws Exception {
        // Get all pipeline directories
        Path pipelinesDir = Paths.get("src/test/resources/pipelines");
        List<String> pipelineNames = new ArrayList<>();
        
        try (Stream<Path> paths = Files.list(pipelinesDir)) {
            paths.filter(Files::isDirectory)
                 .filter(path -> Files.exists(path.resolve(Config.PIPELINE_METADATA_FILE)))
                 .forEach(path -> pipelineNames.add(path.getFileName().toString()));
        }
        
        System.out.println("Found " + pipelineNames.size() + " pipelines to test");
        
        int successCount = 0;
        int failureCount = 0;
        List<String> failedPipelines = new ArrayList<>();
        
        // Execute each pipeline
        for (String pipelineName : pipelineNames) {
            System.out.println("\n========================================");
            System.out.println("Testing pipeline: " + pipelineName);
            System.out.println("========================================");
            
            try {
                testPipelineExecution(pipelineName);
                successCount++;
                System.out.println("✓ " + pipelineName + " - SUCCESS");
            } catch (Exception e) {
                failureCount++;
                failedPipelines.add(pipelineName);
                System.err.println("✗ " + pipelineName + " - FAILED: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        // Print summary
        System.out.println("\n========================================");
        System.out.println("TEST SUMMARY");
        System.out.println("========================================");
        System.out.println("Total pipelines: " + pipelineNames.size());
        System.out.println("Successful: " + successCount);
        System.out.println("Failed: " + failureCount);
        
        if (!failedPipelines.isEmpty()) {
            System.out.println("\nFailed pipelines:");
            failedPipelines.forEach(name -> System.out.println("  - " + name));
        }
        
        // Fail the test if any pipeline failed
        if (failureCount > 0) {
            fail(failureCount + " pipeline(s) failed. See output for details.");
        }
    }
    
    private void testPipelineExecution(String pipelineName) throws Exception {
        String[] args = {
            "--pipeline-name", pipelineName
        };

        // Verify source file exists
        Path sourceFile = Paths.get("src/test/resources/pipelines/" + pipelineName + "/source/local-file.csv");
        assertTrue(Files.exists(sourceFile), 
            "Source file should exist at: " + sourceFile.toAbsolutePath());
        
        // Count source rows using CSVReader (same as CsvUtils)
        long sourceDataRows;
        try (FileReader fileReader = new FileReader(sourceFile.toFile());
             CSVReader csvReader = new CSVReaderBuilder(fileReader).withSkipLines(1).build()) {
            sourceDataRows = csvReader.readAll().size();
        }
        assertTrue(sourceDataRows > 0, 
            "Source file should have at least 1 data row");

        // Delete target file if it exists (to ensure clean test run)
        Path targetFile = Paths.get("src/test/resources/pipelines/" + pipelineName + "/target/local-file.csv");
        if (Files.exists(targetFile)) {
            Files.delete(targetFile);
            System.out.println("  Deleted existing target file: " + targetFile.toAbsolutePath());
        }

        // Act
        Driver.driver(args);

        // Assert
        // Check that target file was created
        Path targetDir = Paths.get("src/test/resources/pipelines/" + pipelineName + "/target");
        assertTrue(Files.exists(targetDir), 
            "Target directory should exist");
        
        assertTrue(Files.exists(targetFile), 
            "Target file should be created at: " + targetFile.toAbsolutePath());
        
        // Count target rows using CSVReader (same as CsvUtils)
        long targetDataRows;
        try (FileReader fileReader = new FileReader(targetFile.toFile());
             CSVReader csvReader = new CSVReaderBuilder(fileReader).withSkipLines(1).build()) {
            targetDataRows = csvReader.readAll().size();
        }
        assertTrue(targetDataRows > 0, 
            "Target file should have at least 1 data row");
        
        // Verify pipeline run directory was created
        String pipelineRunDir = Config.BASE_DIR + "/" + pipelineName + "/" + Config.PIPELINE_START_TIME;
        assertTrue(Files.exists(Paths.get(pipelineRunDir)), 
            "Pipeline run directory should be created");
        
        // Verify summary.json was created
        Path summaryFile = Paths.get(pipelineRunDir, "summary.json");
        assertTrue(Files.exists(summaryFile), 
            "Summary file should be created at: " + summaryFile.toAbsolutePath());
        
        // Check for errors in summary and verify row counts
        Gson gson = new Gson();
        try (FileReader reader = new FileReader(summaryFile.toFile())) {
            JsonObject summary = gson.fromJson(reader, JsonObject.class);
            long errorRowCount = summary.get("errorRowCount").getAsLong();
            
            if (errorRowCount > 0) {
                throw new AssertionError("Pipeline had " + errorRowCount + " error rows");
            }
            
            // Get rowsProcessed from SourceRetrieval and TargetInsertUpdate stages
            Long sourceRowsProcessed = null;
            Long targetRowsProcessed = null;
            
            if (summary.has("stages")) {
                var stages = summary.getAsJsonArray("stages");
                for (int i = 0; i < stages.size(); i++) {
                    JsonObject stage = stages.get(i).getAsJsonObject();
                    String stageName = stage.get("stageName").getAsString();
                    
                    if ("SourceRetrieval".equals(stageName)) {
                        sourceRowsProcessed = stage.get("rowsProcessed").getAsLong();
                    } else if ("TargetInsertUpdate".equals(stageName)) {
                        targetRowsProcessed = stage.get("rowsProcessed").getAsLong();
                    }
                }
            }
            
            // Assert all row counts are equal
            assertNotNull(sourceRowsProcessed, "SourceRetrieval stage should exist in summary");
            assertNotNull(targetRowsProcessed, "TargetInsertUpdate stage should exist in summary");
            
            assertEquals(sourceDataRows, sourceRowsProcessed,
                "Source file data rows (" + sourceDataRows + ") should match SourceRetrieval rowsProcessed (" + sourceRowsProcessed + ")");
            
            assertEquals(sourceDataRows, targetDataRows,
                "Source file data rows (" + sourceDataRows + ") should match target file data rows (" + targetDataRows + ")");
            
            assertEquals(sourceDataRows, targetRowsProcessed,
                "Source file data rows (" + sourceDataRows + ") should match TargetInsertUpdate rowsProcessed (" + targetRowsProcessed + ")");
            
            System.out.println("  Source data rows: " + sourceDataRows);
            System.out.println("  Target data rows: " + targetDataRows);
            System.out.println("  SourceRetrieval rowsProcessed: " + sourceRowsProcessed);
            System.out.println("  TargetInsertUpdate rowsProcessed: " + targetRowsProcessed);
        }
        
        // Verify pmd.json was saved
        Path pmdFile = Paths.get(pipelineRunDir, "pmd.json");
        assertTrue(Files.exists(pmdFile), 
            "PMD file should be saved at: " + pmdFile.toAbsolutePath());
    }

    @Test
    public void testPipelineWithMissingSourceFile() {
        // Arrange
        String pipelineName = "NonExistentPipeline";
        String[] args = {
            "--pipeline-name", pipelineName
        };

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            Driver.driver(args);
        });

        assertTrue(exception.getMessage().contains("Failed to read schema file") || 
                   exception.getMessage().contains("does not exist"),
            "Should throw exception about missing schema or source file");
    }

    @Test
    public void testPipelineWithoutPipelineName() {
        // Arrange
        String[] args = {}; // No pipeline name provided

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            Driver.driver(args);
        });

        assertTrue(exception.getMessage().contains("Pipeline name is required"),
            "Should throw exception about missing pipeline name");
    }
}
