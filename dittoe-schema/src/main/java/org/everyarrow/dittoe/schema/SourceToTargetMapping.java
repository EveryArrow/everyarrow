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

import java.io.Serializable;

/**
 * The SourceToTarget Map holds the intersectional mapping between source and target
 * on an individual field level. This can be a basic fieldA from source becomes fieldA in target.
 * We also could instead say fieldA which is type int becomes a String named FieldC in target
 * Finally, we can use a transformational expressions to transform SourceToTarget. The expressions can
 * be customized or used in construction with Data Expression Analyst Language (DEAL).
 */
public class SourceToTargetMapping implements Serializable {

    /**
     * Source field.
     */
    private Field sourceField;

    /**
     * Target field.
     */
    private Field targetField;

    /**
     * Default constructor.
     */
    public SourceToTargetMapping() {

    }

    /**
     * Parameterized constructor for the Source To Target Mapping.
     *
     * @param sourceField Source field.
     * @param targetField Target field.
     */
    public SourceToTargetMapping(Field sourceField, Field targetField) {
        this.sourceField = sourceField;
        this.targetField = targetField;
    }

    /**
     * Gets the source field.
     *
     * @return Source field.
     */
    public Field getSourceField() {
        return sourceField;
    }

    /**
     * Sets the source field.
     *
     * @param sourceField Source field that is to be set.
     */
    public void setSourceField(Field sourceField) {
        this.sourceField = sourceField;
    }

    /**
     * Gets the target field.
     *
     * @return Target field.
     */
    public Field getTargetField() {
        return targetField;
    }

    /**
     * Sets the target field.
     *
     * @param targetField Target field that is to be set.
     */
    public void setTargetField(Field targetField) {
        this.targetField = targetField;
    }
}
