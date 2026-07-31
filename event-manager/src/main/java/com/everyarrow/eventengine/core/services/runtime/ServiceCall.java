package com.everyarrow.eventengine.core.services.runtime;

import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.eventengine.spi.handler.ServiceCallHandler;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.everyarrow.eventengine.spi.model.ServiceCallResponse;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runtime instance of a ServiceCallDefinition.
 */
public class ServiceCall {

    // UUID for global tracing (engine-level ID)
    private UUID id = UUID.randomUUID();

    // Integer database ID for service_call_log (Postgres PK)
    private Integer dbId;

    private String type;
    private JsonObject params = new JsonObject();
    private final Map<String, Object> metadata = new HashMap<>();
    private ServiceCallSource source = ServiceCallSource.INTERNAL;
    private ServiceCallHandler handler;
    private SecurityContext securityContext;
    private transient Engine engine;

    public ServiceCall() {
    }

    public ServiceCall(Engine engine) {
        this.engine = engine;
    }

    // ---------------------------------------------------------------------
    // Identifiers
    // ---------------------------------------------------------------------

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        if (id != null) {
            this.id = id;
        }
    }

    public Integer getDbId() {
        return dbId;
    }

    public void setDbId(Integer dbId) {
        this.dbId = dbId;
    }

    // ---------------------------------------------------------------------
    // Type + Params
    // ---------------------------------------------------------------------

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public JsonObject getParams() {
        return params;
    }

    public void setParams(JsonObject params) {
        this.params = (params != null) ? params : new JsonObject();
    }

    // ---------------------------------------------------------------------
    // Metadata
    // ---------------------------------------------------------------------

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void putMetadata(String key, Object value) {
        if (key != null) {
            metadata.put(key, value);
        }
    }

    public Object getMetadata(String key) {
        return metadata.get(key);
    }

    public SecurityContext getSecurityContext() {
        return securityContext;
    }

    public void setSecurityContext(SecurityContext securityContext) {
        this.securityContext = securityContext;
    }

    public Engine getEngine() {
        return engine;
    }

    public void setEngine(Engine engine) {
        this.engine = engine;
    }

    /**
     * Execute a generic registry function from a specific plugin registry folder.
     *
     * @param registryFolder plugin registry folder name, e.g. "sample"
     * @param methodName function name inside that registry, e.g. "create_prospect"
     * @param params JSON params passed to the target function
     * @return JSON body returned by the target service
     */
    public JsonElement execute(String registryFolder, String methodName, JsonObject params) throws Exception {
        if (engine == null) {
            throw new IllegalStateException("ServiceCall is not attached to an Engine");
        }
        if (registryFolder == null || registryFolder.isBlank()) {
            throw new IllegalArgumentException("registryFolder is required");
        }
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName is required");
        }

        String serviceType = resolveRegistryServiceType(registryFolder.trim(), methodName.trim());
        return executeServiceType(serviceType, params);
    }

    private JsonElement executeServiceType(String serviceType, JsonObject params) throws Exception {
        ServiceCall child = new ServiceCall(engine);
        child.setType(serviceType);
        child.setParams(params);
        child.setSource(source == null ? ServiceCallSource.INTERNAL : source);
        child.setSecurityContext(SecurityContext.copyOf(securityContext));
        child.getMetadata().putAll(metadata);
        child.putMetadata("parent.serviceCallId", id.toString());

        ServiceCallResponse response = engine.execute(child);
        return response.getBody();
    }

    private String resolveRegistryServiceType(String registryFolder, String methodName) {
        List<String> matches = new ArrayList<>();
        for (ServiceCallDefinition def : engine.getRegistry().listDefinitions()) {
            if (def == null) {
                continue;
            }
            String serviceType = def.getServiceType();
            if (matchesRegistryFolder(registryFolder, def) && matchesMethod(serviceType, methodName)) {
                matches.add(serviceType);
            }
        }

        if (matches.isEmpty() && engine.getRegistry().getDefinition(methodName) != null) {
            matches.add(methodName);
        }
        if (matches.isEmpty()) {
            throw new IllegalArgumentException(
                    "No registry function '" + methodName + "' found in registry category '" + registryFolder + "'");
        }
        if (matches.stream().distinct().count() > 1) {
            throw new IllegalArgumentException(
                    "Registry function '" + methodName + "' is ambiguous in registry category '" + registryFolder
                            + "'. Use the full serviceType.");
        }
        return matches.get(0);
    }

    private boolean matchesRegistryFolder(String registryFolder, ServiceCallDefinition definition) {
        String serviceType = definition.getServiceType();
        String source = definition.getSource();

        if (source != null && source.startsWith("plugin:")) {
            String pluginId = source.substring("plugin:".length());
            int separator = pluginId.indexOf(':');
            if (separator >= 0) {
                pluginId = pluginId.substring(0, separator);
            }
            if (pluginId.equalsIgnoreCase(registryFolder)) {
                return true;
            }
        }

        if (serviceType == null || serviceType.isBlank()) {
            return false;
        }

        String loweredServiceType = serviceType.toLowerCase();
        for (String prefix : registryPrefixes(registryFolder)) {
            if (loweredServiceType.equals(prefix) || loweredServiceType.startsWith(prefix + ".")) {
                return true;
            }
        }
        return false;
    }

    private List<String> registryPrefixes(String registryFolder) {
        String normalized = registryFolder.toLowerCase().trim();
        List<String> prefixes = new ArrayList<>();
        prefixes.add(normalized);
        prefixes.add(normalized.replace('-', '.'));
        prefixes.add(normalized.replace('-', '_'));
        return prefixes.stream().distinct().toList();
    }

    private boolean matchesMethod(String serviceType, String methodName) {
        if (serviceType == null || serviceType.isBlank()) {
            return false;
        }
        if (serviceType.equals(methodName)) {
            return true;
        }

        int lastDot = serviceType.lastIndexOf('.');
        String functionName = lastDot < 0 ? serviceType : serviceType.substring(lastDot + 1);
        return functionName.equals(methodName);
    }

    // ---------------------------------------------------------------------
    // Source + Handler
    // ---------------------------------------------------------------------

    public ServiceCallSource getSource() {
        return source;
    }

    public void setSource(ServiceCallSource source) {
        this.source = source;
    }

    public ServiceCallHandler getHandler() {
        return handler;
    }

    public void setHandler(ServiceCallHandler handler) {
        this.handler = handler;
    }
}
