package com.everyarrow.dittoe.pipelines.behaviors.transformation;

import java.util.Objects;

/** Checked compiler failure carrying a stable diagnostic contract. */
public final class DealCompilationException extends ExpressionEvaluationException {
    private final DealDiagnostic diagnostic;

    public DealCompilationException(DealDiagnostic diagnostic) {
        this(diagnostic, null);
    }

    public DealCompilationException(DealDiagnostic diagnostic, Throwable cause) {
        super(Objects.requireNonNull(diagnostic, "diagnostic").message(), cause);
        this.diagnostic = diagnostic;
    }

    public DealDiagnostic diagnostic() {
        return diagnostic;
    }

    public DealCompilationException forField(String fieldName) {
        return new DealCompilationException(diagnostic.withFieldName(fieldName), this);
    }
}
