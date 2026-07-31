/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.registry;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Raised when candidates with equal precedence name different implementations.
 */
public final class AmbiguousFunctionRegistrationException extends IllegalStateException {

    AmbiguousFunctionRegistrationException(String serviceType,
                                           List<FunctionCandidate> candidates) {
        super(message(serviceType, candidates));
    }

    private static String message(String serviceType,
                                  List<FunctionCandidate> candidates) {
        String evidence = candidates.stream()
                .map(candidate -> candidate.implementation()
                        + " [origin=" + candidate.origin().name().toLowerCase()
                        + ", source=" + safe(candidate.source()) + "]")
                .sorted()
                .collect(Collectors.joining(", "));
        return "Ambiguous registered function '" + serviceType
                + "': equal-precedence candidates name different implementations: "
                + evidence
                + ". Use distinct serviceType namespaces or declare an explicit override relationship.";
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
