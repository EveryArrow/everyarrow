package com.everyarrow.eventengine.core.credits;

/**
 * Result of reserving credits for an execution.
 */
public final class CreditDecision {
    private final CreditReservation reservation;
    private final CreditDenialReason denialReason;
    private final long creditsRequired;
    private final long creditsAvailable;
    private final String message;

    private CreditDecision(CreditReservation reservation,
                           CreditDenialReason denialReason,
                           long creditsRequired,
                           long creditsAvailable,
                           String message) {
        this.reservation = reservation;
        this.denialReason = denialReason;
        this.creditsRequired = creditsRequired;
        this.creditsAvailable = creditsAvailable;
        this.message = message;
    }

    public static CreditDecision allowed(CreditReservation reservation) {
        if (reservation == null) {
            throw new IllegalArgumentException("reservation is required");
        }
        return new CreditDecision(
                reservation,
                null,
                reservation.creditsReserved(),
                reservation.creditsReserved(),
                null);
    }

    public static CreditDecision denied(CreditDenialReason reason,
                                        long creditsRequired,
                                        long creditsAvailable,
                                        String message) {
        if (reason == null) {
            throw new IllegalArgumentException("denial reason is required");
        }
        if (creditsRequired < 0 || creditsAvailable < 0) {
            throw new IllegalArgumentException("credit counts must be non-negative");
        }
        return new CreditDecision(null, reason, creditsRequired, creditsAvailable, message);
    }

    public boolean allowed() {
        return reservation != null;
    }

    public CreditReservation reservation() {
        return reservation;
    }

    public CreditDenialReason denialReason() {
        return denialReason;
    }

    public long creditsRequired() {
        return creditsRequired;
    }

    public long creditsAvailable() {
        return creditsAvailable;
    }

    public String message() {
        return message;
    }
}
