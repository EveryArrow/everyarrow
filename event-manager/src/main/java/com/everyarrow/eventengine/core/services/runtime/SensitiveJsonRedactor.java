package com.everyarrow.eventengine.core.services.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Locale;
import java.util.Set;

final class SensitiveJsonRedactor {

    static final String REDACTED = "[REDACTED]";
    private static final int MAX_DEPTH = 64;
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "accountnumber",
            "accesstoken",
            "authorization",
            "bankaccount",
            "bankaccountnumber",
            "bearertoken",
            "connectionstring",
            "cookie",
            "credential",
            "credentials",
            "dateofbirth",
            "dob",
            "ein",
            "idtoken",
            "jdbcauthentication",
            "jdbcurl",
            "key",
            "masterkey",
            "passphrase",
            "password",
            "pin",
            "privatekey",
            "proxyauthorization",
            "refreshtoken",
            "routingnumber",
            "secret",
            "sessionid",
            "setcookie",
            "signature",
            "socialsecuritynumber",
            "ssn",
            "token");

    private SensitiveJsonRedactor() {
    }

    static JsonElement redact(JsonElement value) {
        return redact(value, 0);
    }

    private static JsonElement redact(JsonElement value, int depth) {
        if (value == null || value.isJsonNull()) {
            return value;
        }
        if (depth >= MAX_DEPTH) {
            return new JsonPrimitive(REDACTED);
        }
        if (value.isJsonArray()) {
            JsonArray redacted = new JsonArray();
            for (JsonElement element : value.getAsJsonArray()) {
                redacted.add(redact(element, depth + 1));
            }
            return redacted;
        }
        if (!value.isJsonObject()) {
            return value.deepCopy();
        }

        JsonObject redacted = new JsonObject();
        for (var entry : value.getAsJsonObject().entrySet()) {
            redacted.add(
                    entry.getKey(),
                    isSensitiveKey(entry.getKey())
                            ? new JsonPrimitive(REDACTED)
                            : redact(entry.getValue(), depth + 1));
        }
        return redacted;
    }

    private static boolean isSensitiveKey(String key) {
        String normalized = key == null
                ? ""
                : key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return SENSITIVE_KEYS.contains(normalized)
                || normalized.endsWith("password")
                || normalized.endsWith("passphrase")
                || normalized.endsWith("secret")
                || normalized.endsWith("token")
                || normalized.endsWith("apikey")
                || normalized.endsWith("privatekey")
                || normalized.endsWith("credential")
                || normalized.endsWith("credentials");
    }
}
