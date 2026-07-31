package com.everyarrow.dittoe.pipelines.behaviors.transformation;

/**
 * Runtime-independent types understood by the embedded DEAL implementation.
 */
public enum DealType {
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
