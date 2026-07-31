package com.everyarrow.dittoe.pipelines.behaviors.transformation;

/** Internal unchecked failure used while the parser and type checker walk the AST. */
public class DealCompileFailure extends IllegalArgumentException {
    private final DealDiagnosticCode code;
    private final DealSourceSpan span;

    public DealCompileFailure(DealDiagnosticCode code, String message) {
        this(code, message, DealSourceSpan.unknown(), null);
    }

    public DealCompileFailure(DealDiagnosticCode code,
                              String message,
                              DealSourceSpan span,
                              Throwable cause) {
        super(message, cause);
        this.code = code;
        this.span = span;
    }

    public DealDiagnosticCode code() {
        return code;
    }

    public DealSourceSpan span() {
        return span;
    }

    public DealCompileFailure at(DealSourceSpan sourceSpan) {
        if (span.known() || !sourceSpan.known()) {
            return this;
        }
        return new DealCompileFailure(code, getMessage(), sourceSpan, this);
    }
}
