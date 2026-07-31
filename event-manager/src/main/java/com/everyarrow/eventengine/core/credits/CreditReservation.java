package com.everyarrow.eventengine.core.credits;

import java.time.Instant;
import java.util.UUID;

/**
 * Admission record created before execution starts.
 */
public record CreditReservation(
        UUID reservationId,
        ExecutionCreditRequest request,
        long creditsReserved,
        Instant reservedAt) {

    public CreditReservation {
        if (reservationId == null) {
            throw new IllegalArgumentException("reservationId is required");
        }
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (creditsReserved < 0) {
            throw new IllegalArgumentException("creditsReserved must be non-negative");
        }
        reservedAt = reservedAt == null ? Instant.now() : reservedAt;
    }

    public static CreditReservation create(ExecutionCreditRequest request, long creditsReserved) {
        return new CreditReservation(UUID.randomUUID(), request, creditsReserved, Instant.now());
    }
}
