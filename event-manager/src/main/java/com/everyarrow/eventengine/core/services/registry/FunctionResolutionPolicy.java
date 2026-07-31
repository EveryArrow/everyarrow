/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.registry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Single deterministic precedence policy shared by handler and route lookup.
 */
final class FunctionResolutionPolicy {

    private FunctionResolutionPolicy() {
    }

    static FunctionResolution resolve(String serviceType,
                                      List<FunctionCandidate> registrations) {
        return resolve(serviceType, registrations, false);
    }

    static FunctionResolution resolveStrict(
            String serviceType,
            List<FunctionCandidate> registrations) {
        return resolve(serviceType, registrations, true);
    }

    private static FunctionResolution resolve(
            String serviceType,
            List<FunctionCandidate> registrations,
            boolean requireOverrideTargets) {
        List<FunctionCandidate> candidates = distinctSorted(registrations);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("At least one function candidate is required");
        }

        Map<FunctionCandidate, FunctionOverrideEvidence> overrideEvidence =
                FunctionOverrideValidator.validate(
                        serviceType,
                        candidates,
                        requireOverrideTargets);
        validateUnambiguousPrecedenceLevels(serviceType, candidates);

        int highestPriority = candidates.stream()
                .mapToInt(FunctionResolutionPolicy::priority)
                .max()
                .orElseThrow();
        List<FunctionCandidate> preferred = candidates.stream()
                .filter(candidate -> priority(candidate) == highestPriority)
                .toList();

        FunctionCandidate selected = preferred.getFirst();
        return new FunctionResolution(
                serviceType,
                selected,
                candidates,
                reason(selected, candidates, overrideEvidence.get(selected)),
                overrideEvidence.get(selected));
    }

    static int priority(FunctionCandidate candidate) {
        if (candidate.kind() == FunctionCandidate.Kind.JAVA_HANDLER
                && candidate.overrideDeclaration() != null) {
            return 450;
        }
        return switch (candidate.kind()) {
            case CORE -> 500;
            case JAVA_HANDLER -> switch (candidate.origin()) {
                case LOCAL_ANNOTATION -> 400;
                case LOCAL_REGISTRY -> 375;
                case SERVER_REGISTRY -> 300;
                case LOCAL_SNAPSHOT -> 275;
                case CORE -> 500;
            };
            case METADATA_ONLY -> switch (candidate.origin()) {
                case LOCAL_ANNOTATION, LOCAL_REGISTRY -> 250;
                case SERVER_REGISTRY -> 225;
                case LOCAL_SNAPSHOT -> 200;
                case CORE -> 500;
            };
            case DB_FUNCTION -> 100;
        };
    }

    private static List<FunctionCandidate> distinctSorted(
            List<FunctionCandidate> registrations) {
        Map<String, FunctionCandidate> distinct = new LinkedHashMap<>();
        registrations.stream()
                .sorted(Comparator.comparing(FunctionCandidate::sortKey))
                .forEach(candidate -> distinct.putIfAbsent(
                        candidate.declarationKey(),
                        candidate));
        return new ArrayList<>(distinct.values());
    }

    private static void validateUnambiguousPrecedenceLevels(
            String serviceType,
            List<FunctionCandidate> candidates) {
        Map<Integer, List<FunctionCandidate>> byPriority = new LinkedHashMap<>();
        for (FunctionCandidate candidate : candidates) {
            byPriority.computeIfAbsent(priority(candidate), ignored -> new ArrayList<>())
                    .add(candidate);
        }

        for (List<FunctionCandidate> peers : byPriority.values()) {
            if (peers.size() < 2) {
                continue;
            }
            FunctionCandidate first = peers.getFirst();
            if (peers.stream().anyMatch(candidate ->
                    !first.equivalentImplementation(candidate))) {
                throw new AmbiguousFunctionRegistrationException(serviceType, peers);
            }
        }
    }

    private static String reason(FunctionCandidate selected,
                                 List<FunctionCandidate> candidates,
                                 FunctionOverrideEvidence overrideEvidence) {
        if (candidates.size() == 1) {
            if (overrideEvidence != null
                    && overrideEvidence.status()
                    == FunctionOverrideEvidence.Status.PENDING_TARGET) {
                return "explicit plugin override is awaiting its declared generated function target";
            }
            return "only registered candidate";
        }
        if (selected.kind() == FunctionCandidate.Kind.CORE) {
            return "reserved core implementation has highest precedence";
        }
        if (overrideEvidence != null
                && overrideEvidence.status() == FunctionOverrideEvidence.Status.VALID) {
            return "explicit plugin override validated against generated function "
                    + overrideEvidence.targetImplementation();
        }
        if (selected.kind() == FunctionCandidate.Kind.JAVA_HANDLER
                && selected.origin() == FunctionRegistrationOrigin.LOCAL_ANNOTATION
                && candidates.stream().anyMatch(candidate ->
                candidate.kind() == FunctionCandidate.Kind.DB_FUNCTION)) {
            return "local annotation handler overrides server-backed generic function";
        }
        if (candidates.stream().allMatch(selected::equivalentImplementation)) {
            return "equivalent duplicate declarations resolve to the same implementation";
        }
        return "selected by deterministic origin and implementation precedence";
    }
}
