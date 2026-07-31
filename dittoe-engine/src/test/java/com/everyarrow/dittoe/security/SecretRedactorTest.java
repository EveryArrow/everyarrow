package com.everyarrow.dittoe.security;

import com.everyarrow.dittoe.utility.security.SecretRedactor;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SecretRedactorTest {
    @Test
    void redactsNestedCredentialKeysWithoutMutatingOriginal() {
        JsonObject root = new JsonObject();
        JsonObject connectionDetails = new JsonObject();
        connectionDetails.addProperty("user", "service");
        connectionDetails.addProperty("password", "super-secret");
        connectionDetails.addProperty("gcsCredentialsJson", "{\"private_key\":\"abc\"}");
        root.add("connectionDetails", connectionDetails);

        JsonObject redacted = SecretRedactor.redact(root);

        assertEquals(SecretRedactor.REDACTED,
                redacted.getAsJsonObject("connectionDetails").get("password").getAsString());
        assertEquals(SecretRedactor.REDACTED,
                redacted.getAsJsonObject("connectionDetails").get("gcsCredentialsJson").getAsString());
        assertEquals("super-secret", root.getAsJsonObject("connectionDetails").get("password").getAsString());
        assertFalse(redacted.toString().contains("super-secret"));
    }
}
