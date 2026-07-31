package com.everyarrow.eventengine.core.services.runtime;

import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class GenericProcessHandlerPerchCorrelationTest {
    private static final String SERVICE_TYPE = "orders.public.order_insert";

    @TempDir
    Path tempDir;

    @Test
    void optedInFunctionReceivesServerDerivedMostSpecificPerchExecutionId() throws Exception {
        Engine engine = engineWithCorrelationService();
        CapturingPostgresHelper postgres = new CapturingPostgresHelper();
        GenericProcessHandler handler = new GenericProcessHandler(engine, postgres);

        JsonObject callerParams = new JsonObject();
        callerParams.addProperty("name", "new order");
        callerParams.addProperty(PerchRuntimeMetadata.DATABASE_CORRELATION_COLUMN, UUID.randomUUID().toString());

        ServiceCall call = new ServiceCall(engine);
        call.setType(SERVICE_TYPE);
        call.setParams(callerParams);
        String groupId = UUID.randomUUID().toString();
        String jobId = UUID.randomUUID().toString();
        String taskId = UUID.randomUUID().toString();
        call.putMetadata(PerchRuntimeMetadata.JOB_GROUP_EXECUTION_INSTANCE_ID, groupId);
        call.putMetadata(PerchRuntimeMetadata.JOB_EXECUTION_INSTANCE_ID, jobId);
        call.putMetadata(PerchRuntimeMetadata.TASK_EXECUTION_INSTANCE_ID, taskId);

        handler.execute(call);

        assertEquals(taskId,
                postgres.params.get(PerchRuntimeMetadata.DATABASE_CORRELATION_COLUMN).getAsString());
        assertEquals("new order", postgres.params.get("name").getAsString());
        assertNotSame(callerParams, postgres.params);
    }

    @Test
    void optedInFunctionRemovesCallerSuppliedCorrelationWithoutRuntimeContext() throws Exception {
        Engine engine = engineWithCorrelationService();
        CapturingPostgresHelper postgres = new CapturingPostgresHelper();
        GenericProcessHandler handler = new GenericProcessHandler(engine, postgres);

        JsonObject callerParams = new JsonObject();
        callerParams.addProperty(PerchRuntimeMetadata.DATABASE_CORRELATION_COLUMN, UUID.randomUUID().toString());
        ServiceCall call = new ServiceCall(engine);
        call.setType(SERVICE_TYPE);
        call.setParams(callerParams);

        handler.execute(call);

        assertFalse(postgres.params.has(PerchRuntimeMetadata.DATABASE_CORRELATION_COLUMN));
    }

    @Test
    void functionWithoutCapabilityRetainsItsOriginalParams() throws Exception {
        Engine engine = new Engine(tempDir.resolve("plain").toFile());
        ServiceCallDefinition definition = ServiceCallDefinition.fromDb(
                SERVICE_TYPE, "orders", "public", "order_insert",
                "POST", "/orders", "Insert order", "json");
        engine.getRegistry().register(SERVICE_TYPE, new GenericProcessHandler(), definition);

        CapturingPostgresHelper postgres = new CapturingPostgresHelper();
        GenericProcessHandler handler = new GenericProcessHandler(engine, postgres);
        JsonObject callerParams = new JsonObject();
        String callerValue = UUID.randomUUID().toString();
        callerParams.addProperty(PerchRuntimeMetadata.DATABASE_CORRELATION_COLUMN, callerValue);
        ServiceCall call = new ServiceCall(engine);
        call.setType(SERVICE_TYPE);
        call.setParams(callerParams);

        handler.execute(call);

        assertEquals(callerValue,
                postgres.params.get(PerchRuntimeMetadata.DATABASE_CORRELATION_COLUMN).getAsString());
    }

    private Engine engineWithCorrelationService() {
        Engine engine = new Engine(tempDir.resolve(UUID.randomUUID().toString()).toFile());
        ServiceCallDefinition.Annotations annotations = new ServiceCallDefinition.Annotations();
        annotations.databaseCorrelation = new ServiceCallDefinition.DatabaseCorrelationMeta();
        annotations.databaseCorrelation.column = PerchRuntimeMetadata.DATABASE_CORRELATION_COLUMN;
        annotations.databaseCorrelation.scope = PerchRuntimeMetadata.DATABASE_CORRELATION_SCOPE;
        ServiceCallDefinition definition = ServiceCallDefinition.fromDb(
                SERVICE_TYPE, "orders", "public", "order_insert",
                "POST", "/orders", "Insert order", "json", annotations);
        engine.getRegistry().register(SERVICE_TYPE, new GenericProcessHandler(), definition);
        return engine;
    }

    private static final class CapturingPostgresHelper extends PostgresHelper {
        private JsonObject params;

        @Override
        public JsonObject processDbFunction(
                String database,
                String schema,
                String function,
                String jsonType,
                JsonObject params) {
            this.params = params;
            return new JsonObject();
        }
    }
}
