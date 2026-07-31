/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.registry;

/**
 * Raised when a declared function override is missing or incompatible.
 */
public final class InvalidFunctionOverrideException extends IllegalStateException {

    InvalidFunctionOverrideException(String message) {
        super(message);
    }
}
