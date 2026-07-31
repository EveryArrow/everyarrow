package com.everyarrow.dittoe.io.database.servers.postgresql;

import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.Resource;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostgreSQLServerConnectionValidationTest {

    @Test
    void connectionOnlyValidationDoesNotRequireAResourceSchema() throws Exception {
        PostgreSQLServer server = server();
        server.setResource(null);

        assertDoesNotThrow(server::validateConnectionProperties);
    }

    @Test
    void resourceValidationStillRequiresASchemaWhenAResourceIsPresent() throws Exception {
        PostgreSQLServer server = server();
        server.setResource(new Resource());

        assertThrows(ExecutionException.class, server::validateConnectionProperties);
    }

    private PostgreSQLServer server() throws Exception {
        PostgreSQLServer server = new PostgreSQLServer(true);
        server.setConnectionDetails(Map.of(
                "host", "localhost",
                "port", "5432",
                "database", "postgres",
                "user", "postgres",
                "password", "test"));
        return server;
    }
}
