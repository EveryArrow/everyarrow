package com.everyarrow.dittoe.pipelines.behaviors.transformation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * DEAL's immutable view of the fields declared by a PMD Resource.
 */
public final class DealSchema {
    private final Map<String, FieldDefinition> fields;

    private DealSchema(Map<String, FieldDefinition> fields) {
        this.fields = Map.copyOf(fields);
    }

    public static DealSchema of(List<FieldDefinition> fieldDefinitions) {
        Objects.requireNonNull(fieldDefinitions, "fieldDefinitions");
        Map<String, FieldDefinition> fields = new LinkedHashMap<>();
        for (FieldDefinition field : fieldDefinitions) {
            Objects.requireNonNull(field, "fieldDefinitions must not contain null");
            if (fields.putIfAbsent(field.name(), field) != null) {
                throw new IllegalArgumentException("Duplicate DEAL field: " + field.name());
            }
        }
        return new DealSchema(fields);
    }

    public FieldDefinition requireField(String name) {
        FieldDefinition field = fields.get(name);
        if (field == null) {
            throw new IllegalArgumentException("Unknown PMD Resource field: " + name);
        }
        return field;
    }

    public Map<String, FieldDefinition> fields() {
        return fields;
    }

    public record FieldDefinition(String name, DealType dataType, boolean nullable, boolean computed) {
        public FieldDefinition {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(dataType, "dataType");
        }
    }
}
