package com.everyarrow.dittoe.pipelines.behaviors.transformation;

/** A DEAL syntax failure with a zero-based source span. */
public final class DealSyntaxException extends DealCompileFailure {

    public DealSyntaxException(String message, int position) {
        this(message, new DealSourceSpan(Math.max(position, 0), Math.max(position, 0) + 1));
    }

    public DealSyntaxException(String message, DealSourceSpan span) {
        super(DealDiagnosticCode.SYNTAX_ERROR,
                message + " at position " + span.start(), span, null);
    }

    public int position() {
        return span().start();
    }
}
