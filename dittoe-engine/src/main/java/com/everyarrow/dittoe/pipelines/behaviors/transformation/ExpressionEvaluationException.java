package com.everyarrow.dittoe.pipelines.behaviors.transformation;

public class ExpressionEvaluationException extends Exception {
    public ExpressionEvaluationException(String message) {
        super(message);
    }

    public ExpressionEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}
