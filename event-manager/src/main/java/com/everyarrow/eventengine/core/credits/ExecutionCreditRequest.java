package com.everyarrow.eventengine.core.credits;

import java.util.Set;

/**
 * Credit request assembled after Security has resolved identity context.
 */
public record ExecutionCreditRequest(
        ExecutionType executionType,
        String serviceType,
        String customerId,
        String licenseId,
        String installationId,
        Set<ExecutionCreditExclusionReason> exclusionReasons) {

    public ExecutionCreditRequest {
        if (executionType == null) {
            throw new IllegalArgumentException("executionType is required");
        }
        serviceType = normalize(serviceType);
        customerId = normalize(customerId);
        licenseId = normalize(licenseId);
        installationId = normalize(installationId);
        exclusionReasons = exclusionReasons == null || exclusionReasons.isEmpty()
                ? Set.of()
                : Set.copyOf(exclusionReasons);
    }

    public static ExecutionCreditRequest billable(ExecutionType executionType,
                                                  String serviceType,
                                                  String customerId,
                                                  String licenseId,
                                                  String installationId) {
        return new ExecutionCreditRequest(
                executionType,
                serviceType,
                customerId,
                licenseId,
                installationId,
                Set.of());
    }

    public boolean billable() {
        return exclusionReasons.isEmpty();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
