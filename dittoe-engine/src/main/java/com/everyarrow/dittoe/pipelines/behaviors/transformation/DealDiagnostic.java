package com.everyarrow.dittoe.pipelines.behaviors.transformation;

import java.util.Objects;

/** A portable compiler diagnostic suitable for engine, API, and editor consumers. */
public record DealDiagnostic(DealDiagnosticCode code,
                             DealDiagnosticSeverity severity,
                             String message,
                             DealSourceSpan span,
                             String fieldName) {
    public DealDiagnostic {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(span, "span");
    }

    public DealDiagnostic withFieldName(String newFieldName) {
        return new DealDiagnostic(code, severity, message, span, newFieldName);
    }
}
