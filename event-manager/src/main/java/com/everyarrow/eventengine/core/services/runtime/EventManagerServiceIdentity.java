package com.everyarrow.eventengine.core.services.runtime;

import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.utilities.environment.Environment;
import com.google.gson.JsonObject;

/**
 * Runtime identity Event Manager uses for trusted internal service work.
 */
public final class EventManagerServiceIdentity {

    public static final String API_KEY_CONFIG = "EA_EVENT_MANAGER_SERVICE_API_KEY";
    public static final String API_KEY_METADATA = "http.x-api-key";

    private final ConfigResolver configResolver;

    public EventManagerServiceIdentity() {
        this(EventManagerServiceIdentity::resolveRuntimeConfig);
    }

    EventManagerServiceIdentity(ConfigResolver configResolver) {
        this.configResolver = configResolver == null ? EventManagerServiceIdentity::resolveRuntimeConfig : configResolver;
    }

    public boolean isConfigured() {
        return !isBlank(apiKey());
    }

    public String apiKey() {
        String value = configResolver.get(API_KEY_CONFIG);
        return isBlank(value) ? null : value.trim();
    }

    public void requireConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException(API_KEY_CONFIG + " is required for Event Manager service-worker calls");
        }
    }

    public void apply(ServiceCall call) {
        applyCredential(call);
        call.setSource(ServiceCallSource.INTERNAL);
    }

    public void applyCredential(ServiceCall call) {
        if (call == null) {
            throw new IllegalArgumentException("ServiceCall is required");
        }

        String key = apiKey();
        if (isBlank(key)) {
            throw new IllegalStateException(API_KEY_CONFIG + " is required for Event Manager service-worker calls");
        }

        call.putMetadata(API_KEY_METADATA, key);
    }

    public ServiceCall newServiceCall(Engine engine) {
        ServiceCall call = new ServiceCall(engine);
        apply(call);
        return call;
    }

    public JsonObject readinessJson() {
        JsonObject json = new JsonObject();
        json.addProperty("configKey", API_KEY_CONFIG);
        json.addProperty("credentialType", "API_KEY");
        json.addProperty("configured", isConfigured());
        return json;
    }

    private static String resolveRuntimeConfig(String key) {
        String property = System.getProperty(key);
        if (!isBlank(property)) {
            return property;
        }
        return Environment.getInstance().get(key, "");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    interface ConfigResolver {
        String get(String key);
    }
}
