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
 * These stages represent the BAPPTE (Begin, Acquire, Process, Package, Transmit, End) process.
 * These are the possible stages a pipeline goes through,
 */
public enum StageType {
    Begin,
    Acquire,
    Process,
    Package,
    Transmit,
    End
}
