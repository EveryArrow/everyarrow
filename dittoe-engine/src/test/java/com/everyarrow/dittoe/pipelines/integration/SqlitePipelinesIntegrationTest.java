package com.everyarrow.dittoe.pipelines.integration;

import com.everyarrow.dittoe.pipelines.execution.Driver;
import com.everyarrow.dittoe.utility.Config;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for SQLite pipelines.
 * Tests the complete pipeline execution from source CSV to SQLite database.
 */
@Execution(ExecutionMode.SAME_THREAD)
public class SqlitePipelinesIntegrationTest {

    private static final String SQLITE_DB_PATH = "src/test/resources/sqlite/target.db";
    private static final String DDL_SCRIPT_PATH = "src/test/resources/sqlite/adventure-works-sqlite-ddl-converted.sql";
    
    // Execution order based on foreign key dependencies (topological sort)
    private static final List<String> PIPELINE_EXECUTION_ORDER = Arrays.asList(
        "AddressType", "BusinessEntity", "ContactType", "CountryRegion", "CreditCard",
        "Culture", "Currency", "Department", "Illustration", "Location",
        "PhoneNumberType", "ProductCategory", "ProductDescription", "ProductModel",
        "SalesReason", "ScrapReason", "Shift", "ShipMethod", "SpecialOffer",
        "SpecialOfferProduct", "TransactionHistoryArchive", "UnitMeasure", "CountryRegionCurrency", "CurrencyRate",
        "Person", "ProductModelIllustration", "ProductModelProductDescriptionCulture", "ProductSubcategory", "SalesTerritory",
        "Vendor", "BusinessEntityContact", "EmailAddress", "Employee", "Password",
        "PersonCreditCard", "PersonPhone", "Product", "StateProvince",
        "BillOfMaterials", "EmployeeDepartmentHistory", "EmployeePayHistory", "JobCandidate", "ProductCostHistory",
        "ProductInventory", "ProductListPriceHistory", "ProductProductPhoto", "ProductReview", "ProductVendor",
        "PurchaseOrderHeader", "SalesPerson", "SalesTaxRate", "ShoppingCartItem", "TransactionHistory",
        "WorkOrder", "BusinessEntityAddress", "PurchaseOrderDetail", "SalesPersonQuotaHistory", "SalesTerritoryHistory",
        "Store", "WorkOrderRouting", "Customer", "SalesOrderHeader", "SalesOrderHeaderSalesReason"
    );

    @BeforeAll
    public static void setUpClass() throws Exception {
        // Set the BASE_DIR to use test resources
        Config.BASE_DIR = "src/test/resources/pipelines";
        
        // Use in-memory instrumentation for tests
        Config.INSTRUMENTATION_SERVICE_TYPE = "inmemory";

        // SQLite supports one writer at a time, so keep pipeline writes serial in this suite.
        Config.PIPELINE_THREADS = 1;
        
        // Set the pipeline metadata file name
        Config.PIPELINE_METADATA_FILE = "local-file-csv-to-sqlite-insert-pmd.json";
    }
    
    @BeforeEach
    public void setUp() throws Exception {
        // Delete existing SQLite database if it exists
        Path dbPath = Paths.get(SQLITE_DB_PATH);
        if (Files.exists(dbPath)) {
            Files.delete(dbPath);
            System.out.println("Deleted existing SQLite database: " + dbPath.toAbsolutePath());
        }
        
        // Create new SQLite database and run DDL
        createDatabaseAndRunDDL();
    }

    private void createDatabaseAndRunDDL() throws Exception {
        System.out.println("Creating SQLite database and running DDL...");
        
        // Read DDL script
        String ddl = Files.readString(Paths.get(DDL_SCRIPT_PATH));
        
        // Connect to SQLite and execute DDL
        String jdbcUrl = "jdbc:sqlite:" + SQLITE_DB_PATH;
        try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
            // Disable auto-commit so we can commit after each statement
            conn.setAutoCommit(false);
            
            try (Statement stmt = conn.createStatement()) {
                // Enable foreign keys in SQLite
                stmt.execute("PRAGMA foreign_keys = ON;");
                conn.commit();
                
                int successCount = 0;
                int failureCount = 0;
                
                // Parse DDL into CREATE TABLE statements
                List<String> createStatements = new ArrayList<>();
                StringBuilder currentStatement = new StringBuilder();
                boolean inCreateTable = false;
                
                for (String line : ddl.split("\n")) {
                    String trimmedLine = line.trim();
                    
                    // Skip empty lines and comments
                    if (trimmedLine.isEmpty() || trimmedLine.startsWith("--")) {
                        continue;
                    }
                    
                    // Check if we're starting a CREATE TABLE statement
                    if (trimmedLine.toUpperCase().startsWith("CREATE TABLE")) {
                        inCreateTable = true;
                        currentStatement = new StringBuilder();
                    }
                    
                    if (inCreateTable) {
                        currentStatement.append(line).append("\n");
                        
                        // Check if statement is complete (ends with );)
                        if (trimmedLine.endsWith(");")) {
                            createStatements.add(currentStatement.toString().trim());
                            inCreateTable = false;
                        }
                    }
                }
                
                System.out.println("Found " + createStatements.size() + " CREATE TABLE statements");
                
                // Execute each CREATE TABLE statement and commit immediately
                for (String sql : createStatements) {
                    try {
                        stmt.execute(sql);
                        conn.commit(); // Commit after each statement
                        successCount++;
                        
                        // Extract table name for logging
                        String tableName = sql.substring(sql.indexOf("TABLE") + 5, sql.indexOf("(")).trim();
                        System.out.println("  ✓ Created table: " + tableName);
                    } catch (SQLException e) {
                        conn.rollback(); // Rollback on error
                        failureCount++;
                        System.err.println("Failed to execute DDL statement: " + e.getMessage());
                        System.err.println("SQL: " + sql.substring(0, Math.min(200, sql.length())) + "...");
                        throw e;
                    }
                }
                
                // Verify tables were created
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='table'");
                int tableCount = 0;
                if (rs.next()) {
                    tableCount = rs.getInt(1);
                }
                rs.close();
                
                System.out.println("\n✓ SQLite database created successfully");
                System.out.println("  Tables created: " + tableCount);
                System.out.println("  DDL statements executed: " + successCount);
                System.out.println("  Failed statements: " + failureCount);
                
                if (tableCount == 0) {
                    throw new Exception("No tables were created! DDL execution may have failed silently.");
                }
            }
        }
    }

    @Test
    public void testAllSqlitePipelinesExecution() throws Exception {
        // Filter pipelines to only include those that exist
        List<String> existingPipelines = new ArrayList<>();
        for (String pipelineName : PIPELINE_EXECUTION_ORDER) {
            Path pipelineDir = Paths.get("src/test/resources/pipelines/" + pipelineName);
            Path pmdFile = pipelineDir.resolve(Config.PIPELINE_METADATA_FILE);
            
            if (Files.exists(pipelineDir) && Files.exists(pmdFile)) {
                existingPipelines.add(pipelineName);
            } else {
                throw new Exception("⚠️  Skipping " + pipelineName + ": Pipeline directory or PMD file not found");
            }
        }
        
        System.out.println("Found " + existingPipelines.size() + " existing pipelines to test (out of " + PIPELINE_EXECUTION_ORDER.size() + " total)");
        
        int successCount = 0;
        int failureCount = 0;
        List<String> failedPipelines = new ArrayList<>();
        
        // Execute each pipeline in dependency order
        for (String pipelineName : existingPipelines) {
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
        System.out.println("Total pipelines tested: " + existingPipelines.size());
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

        // Act - Execute pipeline
        Driver.driver(args);

        // Assert - Verify results
        
        // Verify pipeline run directory was created
        String pipelineRunDir = Config.BASE_DIR + "/" + pipelineName + "/" + Config.PIPELINE_START_TIME;
        assertTrue(Files.exists(Paths.get(pipelineRunDir)), 
            "Pipeline run directory should be created");
        
        // Verify summary.json was created
        Path summaryFile = Paths.get(pipelineRunDir, "summary.json");
        assertTrue(Files.exists(summaryFile), 
            "Summary file should be created at: " + summaryFile.toAbsolutePath());
        
        // Get table name (lowercase pipeline name)
        String tableName = pipelineName.toLowerCase();
        
        // Count rows in SQLite table
        long targetDataRows = countRowsInTable(tableName);
        assertTrue(targetDataRows > 0, 
            "Target table should have at least 1 data row");
        
        // Check for errors in summary and verify row counts
        Gson gson = new Gson();
        try (FileReader reader = new FileReader(summaryFile.toFile())) {
            JsonObject summary = gson.fromJson(reader, JsonObject.class);
            long errorRowCount = summary.get("errorRowCount").getAsLong();
            
            if (errorRowCount > 0) {
                throw new AssertionError("Pipeline " + pipelineName + " had " + errorRowCount + " error rows");
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
                "Source file data rows (" + sourceDataRows + ") should match target table rows (" + targetDataRows + ")");
            
            assertEquals(sourceDataRows, targetRowsProcessed,
                "Source file data rows (" + sourceDataRows + ") should match TargetInsertUpdate rowsProcessed (" + targetRowsProcessed + ")");
            
            System.out.println("  Source data rows: " + sourceDataRows);
            System.out.println("  Target table rows: " + targetDataRows);
            System.out.println("  SourceRetrieval rowsProcessed: " + sourceRowsProcessed);
            System.out.println("  TargetInsertUpdate rowsProcessed: " + targetRowsProcessed);
        }
        
        // Verify pmd.json was saved
        Path pmdFile = Paths.get(pipelineRunDir, "pmd.json");
        assertTrue(Files.exists(pmdFile), 
            "PMD file should be saved at: " + pmdFile.toAbsolutePath());
    }
    
    private long countRowsInTable(String tableName) throws SQLException {
        String jdbcUrl = "jdbc:sqlite:" + SQLITE_DB_PATH;
        String sql = "SELECT COUNT(*) FROM " + tableName;
        
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0;
        }
    }
}
