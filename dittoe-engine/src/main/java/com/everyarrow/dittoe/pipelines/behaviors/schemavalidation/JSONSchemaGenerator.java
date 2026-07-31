package com.everyarrow.dittoe.pipelines.behaviors.schemavalidation;

import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.everyarrow.dittoe.schema.DataType;
import org.everyarrow.dittoe.schema.Field;
import org.everyarrow.dittoe.schema.Resource;

/**
 * Generates a draft 7 JSON schema from DITTOE resource metadata.
 */
public class JSONSchemaGenerator {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Resource resource;

    public JSONSchemaGenerator(Resource resource) {
        this.resource = resource;
    }

    public String generate() throws ExecutionException {
        if (resource == null) {
            throw new ExecutionException("Resource is required to generate JSON schema.");
        }

        ObjectNode schema = OBJECT_MAPPER.createObjectNode();
        schema.put("$schema", "http://json-schema.org/draft-07/schema#");
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");

        for (Field field : resource.getFields()) {
            String fieldName = requireFieldName(field);
            ObjectNode property = properties.putObject(fieldName);
            applyType(property, field.getDataType());
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(schema);
        } catch (JsonProcessingException e) {
            throw new ExecutionException("Failed to serialize generated JSON schema.", e);
        }
    }

    private String requireFieldName(Field field) throws ExecutionException {
        if (field == null || field.getName() == null || field.getName().isBlank()) {
            throw new ExecutionException("JSON schema field names cannot be blank.");
        }
        if (field.getName().indexOf('\u0000') >= 0) {
            throw new ExecutionException("JSON schema field names cannot contain null bytes.");
        }
        return field.getName();
    }

    private void applyType(ObjectNode property, DataType dataType) {
        switch (dataType) {
            case Integer -> property.put("type", "integer");
            case Double, Decimal, Number -> property.put("type", "number");
            case Boolean -> property.put("type", "boolean");
            case Date -> {
                property.put("type", "string");
                property.put("format", "date");
            }
            case DateTime, DateTimeWithTimeZone -> {
                property.put("type", "string");
                property.put("format", "date-time");
            }
            case Time, TimeWithTimeZone -> {
                property.put("type", "string");
                property.put("format", "time");
            }
            default -> property.put("type", "string");
        }
    }

    public Resource getResource() {
        return resource;
    }
}
