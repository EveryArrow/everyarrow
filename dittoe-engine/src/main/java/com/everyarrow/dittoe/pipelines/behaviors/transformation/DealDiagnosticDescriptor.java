package com.everyarrow.dittoe.pipelines.behaviors.transformation;

import java.util.Objects;

/** Editor-facing description of one stable DEAL diagnostic code. */
public record DealDiagnosticDescriptor(DealDiagnosticCode code,
                                       DealDiagnosticSeverity severity,
                                       String description) {
    public DealDiagnosticDescriptor {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(description, "description");
    }
}
