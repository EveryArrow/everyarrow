package com.everyarrow.dittoe.pipelines.behaviors.transformation;

import java.util.Objects;

/** The type and nullability inferred for a compiled DEAL expression. */
public record ExpressionType(Kind kind, boolean nullable) {
    public ExpressionType {
        Objects.requireNonNull(kind, "kind");
        if (kind == Kind.NULL && !nullable) {
            throw new IllegalArgumentException("The DEAL null type must be nullable");
        }
    }

    public static ExpressionType of(DealType dataType, boolean nullable) {
        Objects.requireNonNull(dataType, "dataType");
        return new ExpressionType(Kind.valueOf(dataType.name()), nullable);
    }

    public static ExpressionType any() {
        return new ExpressionType(Kind.ANY, true);
    }

    public static ExpressionType nullValue() {
        return new ExpressionType(Kind.NULL, true);
    }

    public ExpressionType withNullable(boolean newNullable) {
        return kind == Kind.NULL ? this : new ExpressionType(kind, newNullable);
    }

    public boolean isNumeric() {
        return kind == Kind.INTEGER || kind == Kind.DOUBLE
                || kind == Kind.DECIMAL || kind == Kind.NUMBER;
    }

    @Override
    public String toString() {
        if (kind == Kind.NULL || kind == Kind.ANY) {
            return kind.name();
        }
        return kind.name() + (nullable ? " (nullable)" : "");
    }

    public enum Kind {
        ANY,
        NULL,
        STRING,
        INTEGER,
        DOUBLE,
        DECIMAL,
        BOOLEAN,
        DATE,
        DATE_TIME,
        TIME,
        UUID,
        TIME_WITH_TIME_ZONE,
        DATE_TIME_WITH_TIME_ZONE,
        JSON,
        JSONB,
        ARRAY,
        NUMBER,
        BYTES,
        XML
    }
}
