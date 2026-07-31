package com.everyarrow.dittoe.pipelines.execution.credits;

import java.time.Instant;
import java.util.UUID;

/**
 * Minimum credit reservation made before a pipeline starts.
 */
public record PipelineCreditReservation(
        UUID reservationId,
        PipelineCreditRequest request,
        long creditsReserved,
        Instant reservedAt) {

    public PipelineCreditReservation {
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

    public static PipelineCreditReservation create(PipelineCreditRequest request, long creditsReserved) {
        return new PipelineCreditReservation(UUID.randomUUID(), request, creditsReserved, Instant.now());
    }
}
