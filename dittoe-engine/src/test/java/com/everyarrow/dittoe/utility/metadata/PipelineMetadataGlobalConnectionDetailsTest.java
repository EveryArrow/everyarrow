package com.everyarrow.dittoe.utility.metadata;

import com.everyarrow.dittoe.io.IOServer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.everyarrow.dittoe.schema.Resource;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class PipelineMetadataGlobalConnectionDetailsTest {

    @Test
    void resourceAllowsConnectorsWithoutAResourceStrategy() {
        JsonObject resourceJson = JsonParser.parseString("""
                {
                  "resourceID": 1,
                  "resourceName": "Department",
                  "resourceProperties": {"dataset": "AdventureWorks"},
                  "fields": []
                }
                """).getAsJsonObject();

        Resource resource = PipelineMetadataUtils.deserializeResource(resourceJson, true);

        assertEquals("Department", resource.getResourceName());
        assertNull(resource.getStrategy());
    }

    @Test
    void serverUsesPmdGlobalsForMissingConnectionDetailsAndKeepsLocalOverrides() {
        JsonObject serverJson = JsonParser.parseString("""
                {
                  "id": 1,
                  "name": "source-postgres",
                  "type": "Database",
                  "connectionDetails": {
                    "port": "6432"
                  },
                  "additionalProperties": {}
                }
                """).getAsJsonObject();

        Map<String, String> globals = Map.of(
                "databaseType", "PostgreSQLServer",
                "host", "database.example",
                "port", "5432",
                "database", "source_data",
                "user", "reader",
                "password", "secret");

        IOServer server = PipelineMetadataUtils.deserializeServer(
                serverJson,
                new Resource(),
                true,
                globals);

        assertEquals("database.example", server.getConnectionDetails().get("host"));
        assertEquals("6432", server.getConnectionDetails().get("port"));
        assertEquals("source_data", server.getConnectionDetails().get("database"));
        assertFalse(server.getDeclaredConnectionDetails().containsKey("host"));
    }

    @Test
    void serializationDoesNotExpandInheritedConnectionDetailsIntoServer() {
        JsonObject serverJson = JsonParser.parseString("""
                {
                  "id": 2,
                  "name": "target-postgres",
                  "type": "Database",
                  "connectionDetails": {
                    "databaseType": "PostgreSQLServer",
                    "database": "target_data"
                  },
                  "additionalProperties": {}
                }
                """).getAsJsonObject();

        IOServer server = PipelineMetadataUtils.deserializeServer(
                serverJson,
                new Resource(),
                false,
                Map.of("host", "database.example", "port", "5432"));

        JsonObject serializedConnectionDetails = PipelineMetadataUtils.serializeServer(server)
                .getAsJsonObject("connectionDetails");

        assertEquals("target_data", serializedConnectionDetails.get("database").getAsString());
        assertFalse(serializedConnectionDetails.has("host"));
        assertFalse(serializedConnectionDetails.has("port"));
    }
}
