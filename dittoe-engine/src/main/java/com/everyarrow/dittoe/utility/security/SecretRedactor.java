package com.everyarrow.dittoe.utility.security;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SecretRedactor {
    public static final String REDACTED = "[REDACTED]";

    private SecretRedactor() {
    }

    public static JsonObject redact(JsonObject jsonObject) {
        JsonObject copy = jsonObject.deepCopy();
        redactObject(copy);
        return copy;
    }

    public static boolean isSecretKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return normalized.equals("key")
                || normalized.contains("password")
                || normalized.contains("passwd")
                || normalized.contains("secret")
                || normalized.contains("token")
                || normalized.contains("apikey")
                || normalized.contains("accesskey")
                || normalized.contains("privatekey")
                || normalized.contains("credentialsjson")
                || normalized.contains("clientsecret");
    }

    private static void redactObject(JsonObject object) {
        List<String> keys = new ArrayList<>(object.keySet());
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (isSecretKey(key)) {
                object.addProperty(key, REDACTED);
            } else {
                redactElement(value);
            }
        }
    }

    private static void redactElement(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonObject()) {
            redactObject(element.getAsJsonObject());
        } else if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement child : array) {
                redactElement(child);
            }
        }
    }
}
