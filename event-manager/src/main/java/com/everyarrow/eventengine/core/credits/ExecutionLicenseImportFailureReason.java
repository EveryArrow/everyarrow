package com.everyarrow.eventengine.core.credits;

/**
 * Stable import failure reasons returned by the local license import surface.
 */
public enum ExecutionLicenseImportFailureReason {
    TOKEN_REQUIRED,
    INVALID_TOKEN,
    UNSUPPORTED_ALGORITHM,
    KEY_ID_MISMATCH,
    VERIFIER_UNAVAILABLE,
    INSTALLATION_MISMATCH,
    LEDGER_UNAVAILABLE
}
