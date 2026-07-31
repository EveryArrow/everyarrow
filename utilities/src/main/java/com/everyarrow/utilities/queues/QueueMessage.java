/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.queues;

import java.time.Instant;
import java.util.Map;

public record QueueMessage(
        String messageId,
        String correlationId,
        Instant receivedAt,
        byte[] body,
        Map<String, String> attributes
) {}