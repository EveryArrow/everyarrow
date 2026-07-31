/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

/**
 * In-process retry primitives for external-boundary calls.
 *
 * <p>This package is for retrying a single operation before the caller reports
 * failure to its own boundary. It is intentionally separate from queue message
 * redelivery APIs such as {@code QueueAck.retry(...)} and
 * {@code RetryDirective}, which model broker-level retry/dead-letter flows.</p>
 */
package com.everyarrow.utilities.retry;
