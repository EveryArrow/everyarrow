/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.queues.artemis;

public record ArtemisQueueConfig(
        String brokerUrl,
        String username,
        String password,
        long receiveTimeoutMs
) {
    public ArtemisQueueConfig {
        if (brokerUrl == null || brokerUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("brokerUrl is required");
        }
        if (receiveTimeoutMs < 0) {
            throw new IllegalArgumentException("receiveTimeoutMs must be >= 0");
        }
    }
}
