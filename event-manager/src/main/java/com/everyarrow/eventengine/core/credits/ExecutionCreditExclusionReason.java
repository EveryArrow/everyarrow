package com.everyarrow.eventengine.core.credits;

/**
 * Non-billable reasons that keep system plumbing out of execution usage.
 */
public enum ExecutionCreditExclusionReason {
    HEALTH_CHECK,
    READINESS_CHECK,
    LICENSE_CHECK,
    CREDIT_OR_USAGE_CALL,
    ATTESTATION_CALL,
    INTERNAL_SECURITY_CALL,
    INTERNAL_PERCH_CALL,
    AUTHENTICATION_FAILED,
    AUTHORIZATION_FAILED,
    EMPTY_QUEUE_POLL
}
