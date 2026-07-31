/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.queues;

public interface QueueAck {
    void ack();
    void retry(RetryDirective directive);
    void deadLetter(DeadLetterDirective directive);
}