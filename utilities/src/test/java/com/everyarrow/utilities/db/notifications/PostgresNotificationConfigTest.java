/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.db.notifications;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostgresNotificationConfigTest {

    @Test
    void acceptsSimpleIdentifierChannel() {
        PostgresNotificationConfig config = PostgresNotificationConfig.of("app_security_changed");

        assertEquals("app_security_changed", config.channel());
    }

    @Test
    void rejectsUnsafeChannelText() {
        assertThrows(IllegalArgumentException.class,
                () -> PostgresNotificationConfig.of("app_security_changed; drop table principal"));
    }

    @Test
    void requiresPositiveDurations() {
        assertThrows(IllegalArgumentException.class,
                () -> new PostgresNotificationConfig(
                        "app_security_changed",
                        Duration.ZERO,
                        Duration.ofSeconds(1)));
    }
}
