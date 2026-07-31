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
 * The templateType is an enum whose values represent an abstract concept that relate source to target.
 * In a custom implementation of this framework the TemplateType could be something akin to
 * SystemAtoTypeB. Dor example: SalesForceToFile, or FileToMyHRDatabase. To implement custom types
 * you would modify the TemplateType enum items. In you use DITTOE without modifications we provide
 * for 16 standard templates that represent all combinations of source to target type representations.
 * Source To Target representations for FADS (Files, APIs, Databases, Streams).
 */
public enum TemplateType {

    FileToFile,
    FileToDatabase,
    FileToStream,
    FileToAPI,
    DatabaseToDatabase,
    DatabaseToFile,
    DatabaseToStream,
    DatabaseToAPI,
    StreamToStream,
    StreamToFile,
    StreamToDatabase,
    StreamToAPI,
    APIToAPI,
    APIToFile,
    APIToDatabase,
    APIToStream
}
