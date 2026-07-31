package com.everyarrow.dittoe.utility.metadata;


import com.everyarrow.utilities.security.CryptoUtils;
import com.everyarrow.utilities.reflection.Reflection;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.everyarrow.dittoe.io.IOServer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.*;
import com.everyarrow.dittoe.utility.Utils;

import javax.crypto.SecretKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.everyarrow.utilities.json.JsonUtils.existsAndNotNull;


/**
 * Utility class for handling various data operations related to a Pipeline Metadata.
 */
public final class PipelineMetadataUtils {

    // Private constructor to prevent instantiation.
    private PipelineMetadataUtils() {
    }

    /**
     * Encrypts or decrypts specified keys within the pipeline metadata.
     *
     * @param pipelineMetadata The JSON object containing pipeline metadata.
     * @param secretKey        The secret key used for encryption or decryption.
     * @param keysToProtect    A list of keys to be encrypted or decrypted.
     * @param encrypt          A boolean flag indicating whether to encrypt (true) or decrypt (false).
     */
    public static void protectPMDKeys(JsonObject pipelineMetadata, SecretKey secretKey, List<String> keysToProtect, boolean encrypt) {
        // Process behaviors: encrypts or decrypts properties within behaviors.
        if (pipelineMetadata.has("behaviors") && !pipelineMetadata.get("behaviors").isJsonNull()) {
            pipelineMetadata.get("behaviors").getAsJsonArray().forEach(_behavior -> {
                JsonObject behavior = _behavior.getAsJsonObject();

                if (behavior.has("properties") && !behavior.get("properties").isJsonNull()) {
                    JsonObject properties = behavior.get("properties").getAsJsonObject();
                    properties.keySet().forEach(key -> {
                        if (keysToProtect.contains(key.toLowerCase())) {
                            String value = properties.get(key).getAsString();
                            properties.addProperty(key, encrypt ? CryptoUtils.encrypt(value, secretKey) : CryptoUtils.decrypt(value, secretKey));
                        }
                    });
                }
            });
        }

        // Process global properties: encrypts or decrypts global properties.
        if (pipelineMetadata.has("globalProperties") && !pipelineMetadata.get("globalProperties").isJsonNull()) {
            JsonObject properties = pipelineMetadata.get("globalProperties").getAsJsonObject();
            properties.keySet().forEach(key -> {
                if (keysToProtect.contains(key.toLowerCase())) {
                    String value = properties.get(key).getAsString();
                    properties.addProperty(key, encrypt ? CryptoUtils.encrypt(value, secretKey) : CryptoUtils.decrypt(value, secretKey));
                }
            });
        }

        // Process resource servers: encrypts or decrypts server connection details.
        List.of("sourceResourceToProcess", "targetResourceToProcess").forEach(resourceName -> {
            if (pipelineMetadata.has(resourceName) && !pipelineMetadata.get(resourceName).isJsonNull()) {
                JsonObject resource = pipelineMetadata.get(resourceName).getAsJsonObject();

                if (resource.has("server") && !resource.get("server").isJsonNull()) {
                    JsonObject server = resource.get("server").getAsJsonObject();

                    if (server.has("connectionDetails") && !server.get("connectionDetails").isJsonNull()) {
                        JsonObject connectionDetails = server.get("connectionDetails").getAsJsonObject();

                        connectionDetails.keySet().forEach(key -> {
                            if (keysToProtect.contains(key.toLowerCase())) {
                                String value = connectionDetails.get(key).getAsString();
                                connectionDetails.addProperty(key, encrypt ? CryptoUtils.encrypt(value, secretKey) : CryptoUtils.decrypt(value,
                                        secretKey));
                            }
                        });
                    }
                }
            }
        });
    }

    public static JsonObject serializePipelineMetadata(PipelineMetadata pipelineMetadata) {
        JsonObject pipelineMetadataJson = new JsonObject();

        pipelineMetadataJson.addProperty("id", pipelineMetadata.getId());
        pipelineMetadataJson.addProperty("shortName", pipelineMetadata.getShortName());

        pipelineMetadataJson.add("S2TMap", serializeS2TMap(pipelineMetadata.getS2TMap()));
        pipelineMetadataJson.add("behaviors", serializeBehaviors(pipelineMetadata.getBehaviors()));
        pipelineMetadataJson.add("globalProperties", serializeProperties(pipelineMetadata.getGlobalProperties()));

        pipelineMetadataJson.add("sourceResourceToProcess", serializeResource(pipelineMetadata.getSourceResourceToProcess(), true));
        pipelineMetadataJson.add("targetResourceToProcess", serializeResource(pipelineMetadata.getTargetResourceToProcess(), false));

        return pipelineMetadataJson;
    }

    public static JsonArray serializeBehaviors(List<Behavior> behaviors) {
        JsonArray behaviorsJson = new JsonArray();

        for (Behavior behavior : behaviors) {
            behaviorsJson.add(serializeBehavior(behavior));
        }

        return behaviorsJson;
    }

    public static JsonObject serializeBehavior(Behavior behavior) {
        JsonObject behaviorJson = new JsonObject();

        behaviorJson.addProperty("name", behavior.getName());
        behaviorJson.addProperty("description", behavior.getDescription());
        behaviorJson.addProperty("behaviorType", behavior.getBehaviorType().name());
        behaviorJson.add("properties", serializeProperties(behavior.getProperties()));

        return behaviorJson;
    }

    public static JsonObject serializeProperties(Map<String, String> properties) {
        JsonObject propertiesJson = new JsonObject();

        properties.forEach(propertiesJson::addProperty);

        return propertiesJson;
    }

    public static JsonArray serializeS2TMap(List<SourceToTargetMapping> S2TMap) {
        JsonArray S2TMapJson = new JsonArray();

        for (SourceToTargetMapping sourceToTargetMapping : S2TMap) {
            S2TMapJson.add(serializeS2TMap(sourceToTargetMapping));
        }

        return S2TMapJson;
    }

    public static JsonObject serializeS2TMap(SourceToTargetMapping sourceToTargetMapping) {
        JsonObject sourceToTargetMappingJson = new JsonObject();

        sourceToTargetMappingJson.add("sourceField", serializeField(sourceToTargetMapping.getSourceField(), true));
        sourceToTargetMappingJson.add("targetField", serializeField(sourceToTargetMapping.getTargetField(), false));

        return sourceToTargetMappingJson;
    }

    public static JsonArray serializeFields(List<Field> fields, Boolean isSource) {
        JsonArray fieldsJson = new JsonArray();

        for (Field field : fields) {
            fieldsJson.add(serializeField(field, isSource));
        }

        return fieldsJson;
    }

    public static JsonObject serializeField(Field field, Boolean isSource) {
        JsonObject fieldJson = new JsonObject();

        fieldJson.addProperty("name", field.getName());
        fieldJson.addProperty("dataType", field.getDataType().name());
        fieldJson.addProperty("isNullable", field.isNullable());

        if (isSource && field instanceof SourceField)
            fieldJson.addProperty("expression", ((SourceField) field).getExpression());

        return fieldJson;
    }


    public static JsonObject serializeResource(Resource resource, Boolean isSource) {
        JsonObject resourceJson = new JsonObject();

        resourceJson.addProperty("resourceID", resource.getResourceID());
        resourceJson.addProperty("resourceName", resource.getResourceName());
        resourceJson.addProperty("strategy", resource.getStrategy());
        resourceJson.add("resourceProperties", serializeProperties(resource.getResourceProperties()));

        if (resource.getServer() != null)
            resourceJson.add("server", serializeServer(resource.getServer()));
        else
            resourceJson.add("server", null);

        resourceJson.add("fields", serializeFields(resource.getFields(), isSource));

        return resourceJson;
    }


    public static JsonObject serializeServer(Server server) {
        JsonObject serverJson = new JsonObject();

        serverJson.addProperty("id", server.getId());
        serverJson.addProperty("name", server.getName());
        serverJson.addProperty("type", server.getType().name());
        serverJson.add("connectionDetails", serializeProperties(server.getDeclaredConnectionDetails()));

        if (server.getAdditionalProperties() != null)
            serverJson.add("additionalProperties", serializeProperties(server.getAdditionalProperties()));

        return serverJson;
    }

    public static PipelineMetadata deSerializePipelineMetadata(JsonObject serializedMetadata) throws MetadataRepositoryException {
        PipelineMetadata pipelineMetadata = new PipelineMetadata();

        if (existsAndNotNull(serializedMetadata, "id"))

            pipelineMetadata.setId(serializedMetadata.get("id").getAsString());

        pipelineMetadata.setShortName(serializedMetadata.get("shortName").getAsString());

        if (existsAndNotNull(serializedMetadata, "description"))
            pipelineMetadata.setDescription(serializedMetadata.get("description").getAsString());

        if (existsAndNotNull(serializedMetadata, "globalProperties"))
            pipelineMetadata.setGlobalProperties(deserializeProperties(serializedMetadata.get("globalProperties").getAsJsonObject()));

        pipelineMetadata.setS2TMap(deserializeS2TMap(serializedMetadata.get("S2TMap").getAsJsonArray()));
        pipelineMetadata.setSourceResourceToProcess(deserializeResource(
                serializedMetadata.get("sourceResourceToProcess").getAsJsonObject(),
                true,
                pipelineMetadata.getGlobalProperties()));
        pipelineMetadata.setTargetResourceToProcess(deserializeResource(
                serializedMetadata.get("targetResourceToProcess").getAsJsonObject(),
                false,
                pipelineMetadata.getGlobalProperties()));
        pipelineMetadata.setBehaviors(deserializeBehaviors(serializedMetadata.get("behaviors").getAsJsonArray()));


        initServers(pipelineMetadata);

        return pipelineMetadata;
    }

    public static Resource deserializeResource(JsonObject resourceJson, Boolean isSource) {
        return deserializeResource(resourceJson, isSource, Map.of());
    }

    public static Resource deserializeResource(JsonObject resourceJson,
                                               Boolean isSource,
                                               Map<String, String> connectionDefaults) {
        Resource resource = new Resource();

        if (existsAndNotNull(resourceJson, "strategy"))
            resource.setStrategy(resourceJson.get("strategy").getAsString());

        if (existsAndNotNull(resourceJson, "resourceID"))
            resource.setResourceID(resourceJson.get("resourceID").getAsInt());

        if (existsAndNotNull(resourceJson, "resourceName"))
            resource.setResourceName(resourceJson.get("resourceName").getAsString());

        if (existsAndNotNull(resourceJson, "resourceProperties"))
            resource.setResourceProperties(deserializeProperties(resourceJson.get("resourceProperties").getAsJsonObject()));

        if (existsAndNotNull(resourceJson, "fields"))
            resource.setFields(deserializeFields(resourceJson.get("fields").getAsJsonArray(), isSource));

        if (existsAndNotNull(resourceJson, "server")) {
            JsonObject serverJson = resourceJson.get("server").getAsJsonObject();
            resource.setServer(deserializeServer(serverJson, resource, isSource, connectionDefaults));
        }

        return resource;
    }

    public static IOServer deserializeServer(JsonObject serverJson, Resource resource, Boolean isSource) {
        return deserializeServer(serverJson, resource, isSource, Map.of());
    }

    public static IOServer deserializeServer(JsonObject serverJson,
                                             Resource resource,
                                             Boolean isSource,
                                             Map<String, String> connectionDefaults) {
        String serverType = serverJson.get("type").getAsString();
        Map<String, String> declaredConnectionDetails = deserializeProperties(
                serverJson.get("connectionDetails").getAsJsonObject());
        Map<String, String> effectiveConnectionDetails = new HashMap<>();
        if (connectionDefaults != null) {
            effectiveConnectionDetails.putAll(connectionDefaults);
        }
        effectiveConnectionDetails.putAll(declaredConnectionDetails);

        String concreteServerKey = serverType.toLowerCase() + "Type";
        String concreteServer = effectiveConnectionDetails.get(concreteServerKey);
        if (concreteServer == null || concreteServer.isBlank()) {
            throw new IllegalArgumentException("Missing required connection detail: " + concreteServerKey);
        }
        String namespace = "com.everyarrow.dittoe.io.%s.servers.%s.%s".formatted(serverType.toLowerCase(),
                Utils.removeSuffix(concreteServer.toLowerCase(), "server"), concreteServer);
        IOServer server = (IOServer) Reflection.instantiateClass(namespace, isSource);

        if (existsAndNotNull(serverJson, "id"))
            server.setId(serverJson.get("id").getAsInt());

        if (existsAndNotNull(serverJson, "name"))
            server.setName(serverJson.get("name").getAsString());

        server.setType(ServerType.valueOf(serverJson.get("type").getAsString()));

        if (existsAndNotNull(serverJson, "additionalProperties"))
            server.setAdditionalProperties(deserializeProperties(serverJson.get("additionalProperties").getAsJsonObject()));

        server.setConnectionDetails(declaredConnectionDetails);
        server.setConnectionDefaults(connectionDefaults);
        server.setResource(resource);
        return server;
    }

    public static List<Behavior> deserializeBehaviors(JsonArray behaviorJsonArray) {
        List<Behavior> behaviors = new ArrayList<>();
        for (JsonElement behaviorJson : behaviorJsonArray) {
            behaviors.add(deserializeBehavior(behaviorJson.getAsJsonObject()));
        }

        return behaviors;
    }

    public static Behavior deserializeBehavior(JsonObject behaviorJson) {
        Behavior behavior = new Behavior();
        behavior.setName(behaviorJson.get("name").getAsString());

        if (existsAndNotNull(behaviorJson, "description"))
            behavior.setDescription(behaviorJson.get("description").getAsString());

        behavior.setBehaviorType(BehaviorType.valueOf(behaviorJson.get("behaviorType").getAsString()));
        behavior.setProperties(deserializeProperties(behaviorJson.get("properties").getAsJsonObject()));

        return behavior;
    }

    public static List<Field> deserializeFields(JsonArray fieldsJsonArray, boolean isSource) {
        List<Field> fields = new ArrayList<>();
        for (JsonElement fieldJson : fieldsJsonArray) {
            fields.add(deserializeField(fieldJson.getAsJsonObject(), isSource));
        }

        return fields;
    }

    public static Map<String, String> deserializeProperties(JsonObject propertiesJson) {
        Map<String, String> properties = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : propertiesJson.entrySet()) {
            properties.put(entry.getKey(), entry.getValue().getAsString());
        }

        return properties;
    }

    public static List<SourceToTargetMapping> deserializeS2TMap(JsonArray s2TMapJsonArray) {
        List<SourceToTargetMapping> sourceToTargetMappingList = new ArrayList<>();
        for (JsonElement s2T : s2TMapJsonArray) {
            SourceToTargetMapping s2TMap = deserializeS2T(s2T.getAsJsonObject());
            sourceToTargetMappingList.add(s2TMap);
        }

        return sourceToTargetMappingList;
    }

    public static SourceToTargetMapping deserializeS2T(JsonObject s2TJson) {


        return new SourceToTargetMapping(deserializeField(s2TJson.get("sourceField").getAsJsonObject(), true),
                deserializeField(s2TJson.get("targetField").getAsJsonObject(), false));
    }


    public static Field deserializeField(JsonObject fieldJson, boolean isSource) {
        boolean isNullable = false;

        if (existsAndNotNull(fieldJson, "isNullable")) {
            isNullable = fieldJson.get("isNullable").getAsBoolean();
        }

        if (isSource) {
            String expression = null;
            if (existsAndNotNull(fieldJson, "expression")) {
                expression = fieldJson.get("expression").getAsString();
            }

            return new SourceField(fieldJson.get("name").getAsString(),
                    DataType.valueOf(fieldJson.get("dataType").getAsString()),
                    isNullable,
                    expression);
        }

        return new Field(fieldJson.get("name").getAsString(),
                DataType.valueOf(fieldJson.get("dataType").getAsString()), isNullable);
    }


    public static void initServers(PipelineMetadata pipelineMetadata) throws MetadataRepositoryException {
        try {
            IOServer sourceServer = (IOServer) pipelineMetadata.getSourceResourceToProcess().getServer();
            sourceServer.init();
            sourceServer.getReader().init();
            sourceServer.getReader().getReadStrategy().init();

            IOServer targetServer = (IOServer) pipelineMetadata.getTargetResourceToProcess().getServer();
            targetServer.init();
            targetServer.getWriter().init();
            targetServer.getWriter().getWriteStrategy().init();
        } catch (ExecutionException e) {
            throw new MetadataRepositoryException(e);
        }
    }

    public static List<Field> getNonComputedFields(List<Field> fields) {
        return fields.stream().filter(field -> !(field instanceof SourceField) || ((SourceField) field).getExpression() == null || ((SourceField) field).getExpression().isBlank()).toList();
    }

    public static List<SourceField> getComputedFields(List<Field> fields) {
        List<SourceField> computedFields = new ArrayList<>();

        for (Field field : fields) {
            if (field instanceof SourceField sourceField &&
                    sourceField.getExpression() != null &&
                    !sourceField.getExpression().isBlank()) {
                    computedFields.add(sourceField);
            }
        }

        return computedFields;
    }
}
