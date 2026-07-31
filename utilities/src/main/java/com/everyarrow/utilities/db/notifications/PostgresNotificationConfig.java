/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.db.notifications;

import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;

public record PostgresNotificationConfig(
        String channel,
        Duration pollTimeout,
        Duration reconnectDelay) {

    private static final Pattern CHANNEL_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    public PostgresNotificationConfig {
        channel = normalizeChannel(channel);
        pollTimeout = requirePositive(pollTimeout, "pollTimeout");
        reconnectDelay = requirePositive(reconnectDelay, "reconnectDelay");
    }

    public static PostgresNotificationConfig of(String channel) {
        return new PostgresNotificationConfig(channel, Duration.ofMillis(500), Duration.ofSeconds(5));
    }

    private static String normalizeChannel(String channel) {
        String normalized = Objects.requireNonNull(channel, "channel is required").trim();
        if (!CHANNEL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Postgres notification channel must be a simple identifier");
        }
        return normalized;
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
