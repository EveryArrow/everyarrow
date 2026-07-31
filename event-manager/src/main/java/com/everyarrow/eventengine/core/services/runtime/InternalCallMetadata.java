/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.runtime;

/**
 * Stable metadata keys used by optional platform plugins for internal work.
 */
public final class InternalCallMetadata {

    public static final String PERCH_LIFECYCLE_CALL = "perch.internalCall";

    private InternalCallMetadata() {
    }
}
