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
 * How a behavior a collection of behaviors in a PMD should be executed by the engine that operates it.
 * Values include SYNC, ASYNC, QUEUED, JOINED
 */
public enum ProcessExecutionType {
    SYNC, ASYNC, QUEUED, JOINED
}
