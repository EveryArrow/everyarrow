package com.everyarrow.dittoe.pipelines.behaviors.transformation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A validated and dependency-ordered set of computed DEAL fields.
 */
final class TransformationPlan {
    private final DealSchema schema;
    private final List<CompiledField> fields;

    private TransformationPlan(DealSchema schema, List<CompiledField> fields) {
        this.schema = schema;
        this.fields = List.copyOf(fields);
    }

    static TransformationPlan compile(DealSchema schema,
                                      List<FieldExpression> fieldExpressions,
                                      ExpressionEvaluator evaluator)
            throws ExpressionEvaluationException {
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(fieldExpressions, "fieldExpressions");
        Objects.requireNonNull(evaluator, "evaluator");

        Map<String, CompiledField> fieldsByName = new LinkedHashMap<>();
        for (FieldExpression fieldExpression : fieldExpressions) {
            DealSchema.FieldDefinition field = schema.requireField(fieldExpression.name());
            if (!field.computed()) {
                throw compilationException(
                        DealDiagnosticCode.INVALID_COMPUTED_FIELD,
                        "DEAL transformation field is not computed in the PMD Resource: " + field.name(),
                        DealSourceSpan.unknown(), field.name(), null);
            }

            CompiledExpression expression = compileExpression(field, fieldExpression.expression(), evaluator);
            validateReferences(schema, field.name(), expression.referencedFields());
            validateType(schema, field, expression);
            CompiledField compiledField = new CompiledField(field, fieldExpression.expression(), expression);
            if (fieldsByName.putIfAbsent(field.name(), compiledField) != null) {
                throw compilationException(
                        DealDiagnosticCode.DUPLICATE_COMPUTED_FIELD,
                        "Duplicate computed DEAL field: " + field.name(),
                        expression.sourceSpan(), field.name(), null);
            }
        }

        return new TransformationPlan(schema, orderByDependencies(fieldsByName));
    }

    DealSchema schema() {
        return schema;
    }

    List<CompiledField> fields() {
        return fields;
    }

    private static void validateReferences(DealSchema schema, String fieldName, Set<String> references)
            throws ExpressionEvaluationException {
        for (String reference : references) {
            try {
                schema.requireField(reference);
            } catch (IllegalArgumentException e) {
                throw compilationException(
                        DealDiagnosticCode.UNKNOWN_FIELD,
                        "Computed DEAL field '" + fieldName + "' references unknown PMD Resource field '"
                                + reference + "'",
                        DealSourceSpan.unknown(), fieldName, e);
            }
        }
    }

    private static void validateType(DealSchema schema,
                                     DealSchema.FieldDefinition field,
                                     CompiledExpression expression)
            throws ExpressionEvaluationException {
        try {
            ExpressionType inferredType = expression.inferType(schema);
            DealTypeSystem.requireAssignable(
                    inferredType, field, "Computed DEAL field '" + field.name() + "'");
        } catch (DealCompileFailure e) {
            throw compilationException(
                    e.code(),
                    "Failed to type-check computed DEAL field '" + field.name() + "': " + e.getMessage(),
                    e.span().known() ? e.span() : expression.sourceSpan(), field.name(), e);
        } catch (IllegalArgumentException e) {
            throw compilationException(
                    DealDiagnosticCode.TYPE_MISMATCH,
                    "Failed to type-check computed DEAL field '" + field.name() + "': " + e.getMessage(),
                    expression.sourceSpan(), field.name(), e);
        }
    }

    private static List<CompiledField> orderByDependencies(Map<String, CompiledField> fieldsByName)
            throws ExpressionEvaluationException {
        List<CompiledField> ordered = new ArrayList<>(fieldsByName.size());
        Set<String> visiting = new LinkedHashSet<>();
        Set<String> visited = new HashSet<>();
        for (String fieldName : fieldsByName.keySet()) {
            visit(fieldName, fieldsByName, visiting, visited, ordered);
        }
        return ordered;
    }

    private static void visit(String fieldName,
                              Map<String, CompiledField> fieldsByName,
                              Set<String> visiting,
                              Set<String> visited,
                              List<CompiledField> ordered)
            throws ExpressionEvaluationException {
        if (visited.contains(fieldName)) {
            return;
        }
        if (!visiting.add(fieldName)) {
            List<String> cycle = new ArrayList<>(visiting);
            cycle.add(fieldName);
            throw compilationException(
                    DealDiagnosticCode.CIRCULAR_DEPENDENCY,
                    "Circular computed DEAL field dependency: " + String.join(" -> ", cycle),
                    DealSourceSpan.unknown(), fieldName, null);
        }

        CompiledField field = fieldsByName.get(fieldName);
        for (String reference : field.expression().referencedFields()) {
            if (fieldsByName.containsKey(reference)) {
                visit(reference, fieldsByName, visiting, visited, ordered);
            }
        }

        visiting.remove(fieldName);
        visited.add(fieldName);
        ordered.add(field);
    }

    private static CompiledExpression compileExpression(DealSchema.FieldDefinition field,
                                                        String source,
                                                        ExpressionEvaluator evaluator)
            throws ExpressionEvaluationException {
        try {
            return evaluator.compile(source);
        } catch (DealCompilationException e) {
            throw e.forField(field.name());
        }
    }

    private static DealCompilationException compilationException(DealDiagnosticCode code,
                                                                 String message,
                                                                 DealSourceSpan span,
                                                                 String fieldName,
                                                                 Throwable cause) {
        DealDiagnostic diagnostic = new DealDiagnostic(
                code, DealDiagnosticSeverity.ERROR, message, span, fieldName);
        return new DealCompilationException(diagnostic, cause);
    }

    record FieldExpression(String name, String expression) {
        FieldExpression {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(expression, "expression");
        }
    }

    record CompiledField(DealSchema.FieldDefinition field, String source, CompiledExpression expression) {
        CompiledField {
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(expression, "expression");
        }
    }
}
