package com.everyarrow.dittoe.pipelines.execution.credits;

import java.time.Instant;
import java.util.UUID;

/**
 * Final pipeline usage event ready for future finance-schema persistence.
 */
public record PipelineUsageEvent(
        UUID usageEventId,
        PipelineCreditReservation reservation,
        PipelineExecutionOutcome outcome,
        long creditsCharged,
        long rowsRead,
        long rowsWritten,
        Instant occurredAt) {

    public PipelineUsageEvent {
        if (usageEventId == null) {
            throw new IllegalArgumentException("usageEventId is required");
        }
        if (reservation == null) {
            throw new IllegalArgumentException("reservation is required");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("outcome is required");
        }
        if (creditsCharged < 0 || rowsRead < 0 || rowsWritten < 0) {
            throw new IllegalArgumentException("usage counts must be non-negative");
        }
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }

    public static PipelineUsageEvent create(PipelineCreditReservation reservation,
                                            PipelineExecutionOutcome outcome,
                                            long creditsCharged,
                                            long rowsRead,
                                            long rowsWritten) {
        return new PipelineUsageEvent(
                UUID.randomUUID(),
                reservation,
                outcome,
                creditsCharged,
                rowsRead,
                rowsWritten,
                Instant.now());
    }
}
