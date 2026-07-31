package com.everyarrow.dittoe.pipelines.behaviors.transformation;

/** Stable, machine-readable codes emitted by the DEAL compiler. */
public enum DealDiagnosticCode {
    SYNTAX_ERROR("The expression does not conform to DEAL syntax"),
    UNKNOWN_FUNCTION("The expression calls a function not present in the DEAL catalog"),
    INVALID_ARGUMENT_COUNT("A DEAL function received an unsupported number of arguments"),
    UNKNOWN_FIELD("The expression references a field not declared by the PMD Resource"),
    TYPE_MISMATCH("An operator, function, or computed field received an incompatible type"),
    NULLABILITY_MISMATCH("A nullable expression flows into a non-nullable operation or field"),
    UNSUPPORTED_CONVERSION("An explicit DEAL conversion does not support the source type"),
    INVALID_COMPUTED_FIELD("A transformation field is not a computed PMD Resource field"),
    DUPLICATE_COMPUTED_FIELD("A computed PMD Resource field is declared more than once"),
    CIRCULAR_DEPENDENCY("Computed PMD Resource fields contain a dependency cycle");

    private final String description;

    DealDiagnosticCode(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
