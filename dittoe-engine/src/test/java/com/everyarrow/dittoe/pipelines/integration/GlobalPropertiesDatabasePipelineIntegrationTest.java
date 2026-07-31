package com.everyarrow.dittoe.pipelines.integration;

import com.everyarrow.dittoe.pipelines.execution.Driver;
import com.everyarrow.dittoe.io.database.servers.jdbc.JDBCServer;
import com.everyarrow.dittoe.utility.Config;
import com.everyarrow.dittoe.utility.metadata.MetadataRepositoryAPI;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.apache.logging.log4j.LogManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Execution(ExecutionMode.SAME_THREAD)
class GlobalPropertiesDatabasePipelineIntegrationTest {
    private static final String PIPELINE_NAME = "global-database-fallback";

    @TempDir
    Path tempDirectory;

    private String previousBaseDirectory;
    private String previousMetadataFile;
    private String previousInstrumentationType;
    private int previousPipelineThreads;

    @BeforeEach
    void configurePipelineRuntime() {
        previousBaseDirectory = Config.BASE_DIR;
        previousMetadataFile = Config.PIPELINE_METADATA_FILE;
        previousInstrumentationType = Config.INSTRUMENTATION_SERVICE_TYPE;
        previousPipelineThreads = Config.PIPELINE_THREADS;

        Config.BASE_DIR = tempDirectory.toString();
        Config.PIPELINE_METADATA_FILE = "pmd.json";
        Config.INSTRUMENTATION_SERVICE_TYPE = "inmemory";
        Config.PIPELINE_THREADS = 1;
    }

    @AfterEach
    void restorePipelineRuntime() {
        closeDatabaseServers();
        LogManager.shutdown();
        Config.BASE_DIR = previousBaseDirectory;
        Config.PIPELINE_METADATA_FILE = previousMetadataFile;
        Config.INSTRUMENTATION_SERVICE_TYPE = previousInstrumentationType;
        Config.PIPELINE_THREADS = previousPipelineThreads;
    }

    private void closeDatabaseServers() {
        try {
            var metadata = MetadataRepositoryAPI.getPipelineMetadata();
            if (metadata.getSourceResourceToProcess().getServer() instanceof JDBCServer sourceServer) {
                sourceServer.close();
            }
            if (metadata.getTargetResourceToProcess().getServer() instanceof JDBCServer targetServer) {
                targetServer.close();
            }
        } catch (Exception ignored) {
            // No pipeline metadata was initialized, so there are no pools to close.
        }
    }

    @Test
    void movesDataBetweenDatabasesUsingGlobalDefaultsAndSourceOverride() throws Exception {
        Path sourceDatabase = tempDirectory.resolve("source.db");
        Path targetDatabase = tempDirectory.resolve("target.db");
        createSourceDatabase(sourceDatabase);

        Path pipelineDirectory = Files.createDirectories(tempDirectory.resolve(PIPELINE_NAME));
        Files.writeString(
                pipelineDirectory.resolve(Config.PIPELINE_METADATA_FILE),
                pipelineMetadata(sourceDatabase, targetDatabase).toString());

        Driver.run(new String[]{"--pipeline-name", PIPELINE_NAME});

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + targetDatabase);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("select count(*) from target_items")) {
            assertEquals(2, resultSet.getInt(1));
        }
    }

    private void createSourceDatabase(Path sourceDatabase) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + sourceDatabase);
             Statement statement = connection.createStatement()) {
            statement.execute("create table source_items (id integer not null, value text not null)");
            statement.execute("insert into source_items (id, value) values (1, 'one'), (2, 'two')");
        }
    }

    private JsonObject pipelineMetadata(Path sourceDatabase, Path targetDatabase) {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("id", "global-properties-database-pipeline");
        metadata.addProperty("shortName", "Global properties database pipeline");
        metadata.add("S2TMap", mappings());
        metadata.add("behaviors", behaviors());

        JsonObject globals = new JsonObject();
        globals.addProperty("databaseType", "SQLiteServer");
        globals.addProperty("path", targetDatabase.toString());
        metadata.add("globalProperties", globals);

        metadata.add("sourceResourceToProcess", resource(
                "source_items",
                "com.everyarrow.dittoe.io.database.servers.jdbc.reader.strategy.SelectStrategy",
                sourceDatabase.toString()));
        metadata.add("targetResourceToProcess", resource(
                "target_items",
                "com.everyarrow.dittoe.io.database.servers.jdbc.writer.strategy.InsertStrategy",
                null));
        return metadata;
    }

    private JsonObject resource(String name, String strategy, String localPathOverride) {
        JsonObject resource = new JsonObject();
        resource.addProperty("resourceID", "source_items".equals(name) ? 1 : 2);
        resource.addProperty("resourceName", name);
        resource.addProperty("strategy", strategy);
        resource.add("resourceProperties", new JsonObject());
        resource.add("fields", fields());

        JsonObject server = new JsonObject();
        server.addProperty("id", "source_items".equals(name) ? 1 : 2);
        server.addProperty("name", name + "-server");
        server.addProperty("type", "Database");
        server.add("additionalProperties", new JsonObject());

        JsonObject connectionDetails = new JsonObject();
        if (localPathOverride != null) {
            connectionDetails.addProperty("path", localPathOverride);
        }
        server.add("connectionDetails", connectionDetails);
        resource.add("server", server);
        return resource;
    }

    private JsonArray fields() {
        JsonArray fields = new JsonArray();
        fields.add(field("id", "Integer"));
        fields.add(field("value", "String"));
        return fields;
    }

    private JsonObject field(String name, String dataType) {
        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("dataType", dataType);
        field.addProperty("isNullable", false);
        return field;
    }

    private JsonArray mappings() {
        JsonArray mappings = new JsonArray();
        mappings.add(mapping("id", "Integer"));
        mappings.add(mapping("value", "String"));
        return mappings;
    }

    private JsonObject mapping(String name, String dataType) {
        JsonObject mapping = new JsonObject();
        mapping.add("sourceField", field(name, dataType));
        mapping.add("targetField", field(name, dataType));
        return mapping;
    }

    private JsonArray behaviors() {
        JsonArray behaviors = new JsonArray();
        behaviors.add(behavior("SourceRetrieval"));
        behaviors.add(behavior("TargetInsertUpdate"));
        behaviors.add(behavior("Instrumentation"));
        return behaviors;
    }

    private JsonObject behavior(String type) {
        JsonObject behavior = new JsonObject();
        behavior.addProperty("name", type);
        behavior.addProperty("behaviorType", type);
        behavior.add("properties", new JsonObject());
        return behavior;
    }
}
