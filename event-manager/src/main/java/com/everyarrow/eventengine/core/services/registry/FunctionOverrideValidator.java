/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.registry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates explicit plugin overrides against their generated function
 * contracts without depending on registration order.
 */
final class FunctionOverrideValidator {

    private FunctionOverrideValidator() {
    }

    static Map<FunctionCandidate, FunctionOverrideEvidence> validate(
            String serviceType,
            List<FunctionCandidate> candidates,
            boolean requireTargets) {
        for (FunctionCandidate candidate : candidates) {
            validateContract(serviceType, candidate);
        }
        rejectUndeclaredContractOverrides(serviceType, candidates);

        Map<FunctionCandidate, FunctionOverrideEvidence> evidence =
                new LinkedHashMap<>();
        for (FunctionCandidate candidate : candidates) {
            if (candidate.overrideDeclaration() == null) {
                continue;
            }
            evidence.put(candidate, validateOverride(
                    serviceType,
                    candidate,
                    candidates,
                    requireTargets));
        }
        return evidence;
    }

    private static void validateContract(
            String serviceType,
            FunctionCandidate candidate) {
        FunctionContractIdentity contract = candidate.contract();
        if (contract == null) {
            return;
        }
        String prefix = "Function contract for '" + serviceType + "' from "
                + candidate.implementation();
        String expectedNamespace = namespace(serviceType);
        require(contract.namespace(), prefix + " is missing namespace");
        if (!contract.namespace().equals(expectedNamespace)) {
            throw invalid(prefix + " declares namespace '" + contract.namespace()
                    + "' but serviceType requires '" + expectedNamespace + "'");
        }
        require(contract.version(), prefix + " is missing version");
        FunctionVersionRange.validateVersion(
                contract.version(),
                prefix + " version");
        require(contract.signature(), prefix + " is missing signature");
        if (contract.capabilities().isEmpty()) {
            throw invalid(prefix + " must declare at least one capability");
        }
        require(candidate.pluginId(), prefix + " is missing plugin ownership");
        require(candidate.pluginVersion(), prefix + " is missing plugin version");
        FunctionVersionRange.validateVersion(
                candidate.pluginVersion(),
                prefix + " plugin version");
    }

    private static void rejectUndeclaredContractOverrides(
            String serviceType,
            List<FunctionCandidate> candidates) {
        List<FunctionCandidate> generated = candidates.stream()
                .filter(candidate ->
                        candidate.kind() == FunctionCandidate.Kind.DB_FUNCTION
                                && candidate.contract() != null)
                .toList();
        if (generated.isEmpty()) {
            return;
        }

        for (FunctionCandidate candidate : candidates) {
            if (candidate.kind() != FunctionCandidate.Kind.JAVA_HANDLER
                    || candidate.overrideDeclaration() != null
                    || candidate.pluginId() == null
                    || (candidate.origin() != FunctionRegistrationOrigin.LOCAL_ANNOTATION
                    && candidate.origin() != FunctionRegistrationOrigin.LOCAL_REGISTRY)) {
                continue;
            }
            FunctionCandidate target = generated.getFirst();
            throw invalid(
                    "Plugin handler " + candidate.implementation()
                            + " collides with contract-bearing generated function "
                            + target.implementation() + " for '" + serviceType
                            + "' but declares no explicit functionOverride relationship");
        }
    }

    private static FunctionOverrideEvidence validateOverride(
            String serviceType,
            FunctionCandidate candidate,
            List<FunctionCandidate> candidates,
            boolean requireTarget) {
        FunctionOverrideDeclaration declaration =
                candidate.overrideDeclaration();
        String prefix = "Function override for '" + serviceType + "' from "
                + candidate.implementation();

        if (candidate.kind() != FunctionCandidate.Kind.JAVA_HANDLER) {
            throw invalid(prefix + " must be implemented by a Java handler");
        }
        if (candidate.contract() == null) {
            throw invalid(prefix + " must declare functionContract metadata");
        }
        require(declaration.targetServiceType(),
                prefix + " is missing targetServiceType");
        if (!serviceType.equals(declaration.targetServiceType())) {
            throw invalid(prefix + " targets serviceType '"
                    + declaration.targetServiceType()
                    + "' instead of its registered serviceType");
        }
        require(declaration.targetPluginId(),
                prefix + " is missing targetPluginId");
        require(declaration.versionRange(),
                prefix + " is missing versionRange");
        FunctionVersionRange range = FunctionVersionRange.parse(
                declaration.versionRange(),
                prefix + " versionRange");

        List<FunctionCandidate> targets = candidates.stream()
                .filter(target -> target.kind() == FunctionCandidate.Kind.DB_FUNCTION)
                .filter(target -> serviceType.equals(target.serviceType()))
                .filter(target -> declaration.targetPluginId()
                        .equals(target.pluginId()))
                .toList();
        if (targets.isEmpty()) {
            if (requireTarget) {
                throw invalid(prefix + " cannot find generated target plugin '"
                        + declaration.targetPluginId() + "'");
            }
            return new FunctionOverrideEvidence(
                    FunctionOverrideEvidence.Status.PENDING_TARGET,
                    declaration.targetServiceType(),
                    declaration.targetPluginId(),
                    null,
                    null,
                    null,
                    declaration.versionRange(),
                    false,
                    false,
                    false,
                    false);
        }
        if (targets.size() > 1) {
            throw invalid(prefix + " matches multiple generated targets from plugin '"
                    + declaration.targetPluginId() + "'");
        }

        FunctionCandidate target = targets.getFirst();
        FunctionContractIdentity targetContract = target.contract();
        if (targetContract == null) {
            throw invalid(prefix + " targets " + target.implementation()
                    + " which does not declare functionContract metadata");
        }

        boolean namespaceCompatible = candidate.contract().namespace()
                .equals(targetContract.namespace());
        boolean signatureCompatible = candidate.contract().signature()
                .equals(targetContract.signature());
        Set<String> overrideCapabilities =
                Set.copyOf(candidate.contract().capabilities());
        boolean capabilitiesCompatible =
                overrideCapabilities.containsAll(targetContract.capabilities());
        boolean versionCompatible = range.contains(
                targetContract.version(),
                prefix + " target version");

        if (!namespaceCompatible) {
            throw invalid(prefix + " namespace '" + candidate.contract().namespace()
                    + "' is incompatible with target namespace '"
                    + targetContract.namespace() + "'");
        }
        if (!signatureCompatible) {
            throw invalid(prefix + " signature '" + candidate.contract().signature()
                    + "' is incompatible with target signature '"
                    + targetContract.signature() + "'");
        }
        if (!capabilitiesCompatible) {
            throw invalid(prefix + " capabilities "
                    + candidate.contract().capabilities()
                    + " do not satisfy target capabilities "
                    + targetContract.capabilities());
        }
        if (!versionCompatible) {
            throw invalid(prefix + " versionRange '"
                    + declaration.versionRange()
                    + "' does not include target function version '"
                    + targetContract.version() + "'");
        }

        return new FunctionOverrideEvidence(
                FunctionOverrideEvidence.Status.VALID,
                declaration.targetServiceType(),
                target.pluginId(),
                target.pluginVersion(),
                targetContract.version(),
                target.implementation(),
                declaration.versionRange(),
                true,
                true,
                true,
                true);
    }

    private static String namespace(String serviceType) {
        int separator = serviceType.lastIndexOf('.');
        return separator < 0 ? "" : serviceType.substring(0, separator);
    }

    private static void require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw invalid(message);
        }
    }

    private static InvalidFunctionOverrideException invalid(String message) {
        return new InvalidFunctionOverrideException(message);
    }
}
