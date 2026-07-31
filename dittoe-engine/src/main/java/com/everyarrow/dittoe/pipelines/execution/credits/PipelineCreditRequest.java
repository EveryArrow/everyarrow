package com.everyarrow.dittoe.pipelines.execution.credits;

/**
 * Pipeline credit request assembled before a pipeline run is accepted.
 */
public record PipelineCreditRequest(
        String pipelineName,
        String customerId,
        String licenseId,
        String installationId) {

    public PipelineCreditRequest {
        pipelineName = requireText(pipelineName, "pipelineName");
        customerId = normalize(customerId);
        licenseId = normalize(licenseId);
        installationId = normalize(installationId);
    }

    private static String requireText(String value, String fieldName) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
