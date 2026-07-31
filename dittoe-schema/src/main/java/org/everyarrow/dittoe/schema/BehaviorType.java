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
 * Represents the types of behaviors. Each type represent an activity/task that will be done during the execution of the
 * Pipeline.
 */
public enum BehaviorType {
    Formatting,
    SourceRetrieval,
    TargetInsertUpdate,
    Transformation,
    Encryption,
    Logging,
    Instrumentation,
    lineageTracking,
    KeyGeneration,
    Masking,
    Deduplication,
    SchemaEvolution,
    Retry,
    SchemaValidation,
    DQRuleChecks,
    Archive,
    MicroBatch,
    DataScienceAlgorithms,
    CustomCode,
    Compression,
    ExternalWidget,
    Cache
}
	
	


