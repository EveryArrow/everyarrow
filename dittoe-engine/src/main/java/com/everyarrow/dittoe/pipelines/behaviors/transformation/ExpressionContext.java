package com.everyarrow.dittoe.pipelines.behaviors.transformation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class ExpressionContext {
    private final Map<String, Object> variables;
    private final DealSchema schema;

    private ExpressionContext(Map<String, Object> variables, DealSchema schema) {
        this.variables = new HashMap<>(variables);
        this.schema = schema;
    }

    /**
     * Creates an unrestricted context for compatibility with direct DEAL evaluation.
     */
    public static ExpressionContext of(Map<String, Object> variables) {
        return new ExpressionContext(Objects.requireNonNull(variables, "variables"), null);
    }

    /**
     * Creates a context whose visible variables are defined by the PMD Resource fields.
     */
    public static ExpressionContext forRow(DealSchema schema, Map<String, Object> row) {
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(row, "row");

        Map<String, Object> variables = new HashMap<>();
        for (String fieldName : schema.fields().keySet()) {
            variables.put(fieldName, row.get(fieldName));
        }
        return new ExpressionContext(variables, schema);
    }

    public Map<String, Object> variables() {
        return Collections.unmodifiableMap(variables);
    }

    public Object requireVariable(String name) {
        requireDeclaredField(name);
        if (!variables.containsKey(name)) {
            throw new IllegalArgumentException("Undefined variable: " + name);
        }
        return variables.get(name);
    }

    public void setVariable(String name, Object value) {
        requireDeclaredField(name);
        variables.put(name, value);
    }

    private void requireDeclaredField(String name) {
        if (schema != null) {
            schema.requireField(name);
        }
    }
}
