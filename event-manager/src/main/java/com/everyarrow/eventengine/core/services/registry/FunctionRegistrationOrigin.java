/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.registry;

import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;

/**
 * Describes where a runtime function candidate was obtained.
 *
 * <p>The origin is deliberately separate from the definition's free-form
 * {@code source}. Resolution policy can therefore distinguish a live local
 * annotation from a persisted copy of the same metadata.</p>
 */
public enum FunctionRegistrationOrigin {
    CORE,
    LOCAL_ANNOTATION,
    LOCAL_REGISTRY,
    SERVER_REGISTRY,
    LOCAL_SNAPSHOT;

    static FunctionRegistrationOrigin infer(ServiceCallDefinition definition) {
        String source = definition == null ? null : definition.getSource();
        if (source != null && source.startsWith("core")) {
            return CORE;
        }
        return FunctionCandidate.isDbBacked(definition)
                ? LOCAL_REGISTRY
                : LOCAL_ANNOTATION;
    }
}
