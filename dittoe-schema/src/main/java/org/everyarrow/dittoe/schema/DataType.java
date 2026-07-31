/*
 * DITTOE SCHEMA:
 *
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 * You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 */

package org.everyarrow.dittoe.schema;

/**
 * Type of the data of a Field.
 */
public enum DataType {
    String,
    Integer,
    Double,
    Decimal,
    Boolean,
    Date,
    DateTime,
    Time,
    UUID,
    TimeWithTimeZone,
    DateTimeWithTimeZone,
    JSON,
    JSONB,
    Array,
    Number,
    Bytes,
    XML
}
