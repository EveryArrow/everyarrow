package com.everyarrow.eventengine.core.credits;

/**
 * Stable denial reasons returned by future credit gates.
 */
public enum CreditDenialReason {
    LICENSE_REQUIRED,
    LICENSE_EXPIRED,
    CREDITS_EXHAUSTED,
    LEDGER_UNAVAILABLE,
    ATTESTATION_REQUIRED,
    TAMPER_DETECTED
}
