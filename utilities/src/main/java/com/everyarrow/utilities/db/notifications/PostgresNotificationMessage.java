/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.db.notifications;

public record PostgresNotificationMessage(String channel, int processId, String payload) {
}
