package com.everyarrow.eventengine.core.credits;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable usage event ready for later persistence to finance schema tables.
 */
public record ExecutionUsageEvent(
        UUID usageEventId,
        CreditReservation reservation,
        ExecutionUsageFinalization finalization,
        Instant occurredAt) {

    public ExecutionUsageEvent {
        if (usageEventId == null) {
            throw new IllegalArgumentException("usageEventId is required");
        }
        if (reservation == null) {
            throw new IllegalArgumentException("reservation is required");
        }
        if (finalization == null) {
            throw new IllegalArgumentException("finalization is required");
        }
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }

    public static ExecutionUsageEvent create(CreditReservation reservation,
                                             ExecutionUsageFinalization finalization) {
        return new ExecutionUsageEvent(UUID.randomUUID(), reservation, finalization, Instant.now());
    }
}
