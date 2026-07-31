package com.everyarrow.eventengine.core.credits;

/**
 * Validation failure before a license import reaches the finance ledger.
 */
public class ExecutionLicenseImportException extends RuntimeException {
    private final ExecutionLicenseImportFailureReason reason;

    public ExecutionLicenseImportException(ExecutionLicenseImportFailureReason reason, String message) {
        super(message);
        this.reason = reason == null
                ? ExecutionLicenseImportFailureReason.INVALID_TOKEN
                : reason;
    }

    public ExecutionLicenseImportFailureReason reason() {
        return reason;
    }
}
