package com.everyarrow.eventengine.core.services.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServiceCallLogStoreTest {

    @Test
    void auditPayloadRedactsSecretsRecursivelyWithoutMutatingServiceCall() {
        JsonObject nested = new JsonObject();
        nested.addProperty("password", "database-password");
        nested.addProperty("displayName", "Visible");

        JsonObject headers = new JsonObject();
        headers.addProperty("Authorization", "Bearer secret-token");
        headers.addProperty("Content-Type", "application/json");

        JsonArray records = new JsonArray();
        JsonObject bankRecord = new JsonObject();
        bankRecord.addProperty("routing_number", "123456789");
        bankRecord.addProperty("amount", 42);
        records.add(bankRecord);

        JsonObject params = new JsonObject();
        params.addProperty("api_key", "api-secret");
        params.addProperty("ssn", "111-22-3333");
        params.addProperty("customerId", "customer-123");
        params.add("database", nested);
        params.add("headers", headers);
        params.add("records", records);

        ServiceCall serviceCall = new ServiceCall();
        serviceCall.setId(UUID.randomUUID());
        serviceCall.setType("test.audit");
        serviceCall.setParams(params);

        JsonObject audited = new ServiceCallLogStore(null)
                .basePayload(serviceCall)
                .getAsJsonObject("params");

        assertEquals(SensitiveJsonRedactor.REDACTED, audited.get("api_key").getAsString());
        assertEquals(SensitiveJsonRedactor.REDACTED, audited.get("ssn").getAsString());
        assertEquals("customer-123", audited.get("customerId").getAsString());
        assertEquals(SensitiveJsonRedactor.REDACTED,
                audited.getAsJsonObject("database").get("password").getAsString());
        assertEquals("Visible", audited.getAsJsonObject("database").get("displayName").getAsString());
        assertEquals(SensitiveJsonRedactor.REDACTED,
                audited.getAsJsonObject("headers").get("Authorization").getAsString());
        assertEquals("application/json",
                audited.getAsJsonObject("headers").get("Content-Type").getAsString());
        assertEquals(SensitiveJsonRedactor.REDACTED,
                audited.getAsJsonArray("records").get(0).getAsJsonObject().get("routing_number").getAsString());
        assertEquals(42,
                audited.getAsJsonArray("records").get(0).getAsJsonObject().get("amount").getAsInt());

        assertEquals("database-password", params.getAsJsonObject("database").get("password").getAsString());
        assertEquals("Bearer secret-token", params.getAsJsonObject("headers").get("Authorization").getAsString());
    }
}
