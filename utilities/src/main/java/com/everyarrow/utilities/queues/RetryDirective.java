/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.queues;

import java.time.Duration;

public record RetryDirective(
        int attempt,
        Duration delay,
        String reason
) {}
