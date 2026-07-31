/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ConnectionSecurityTest {

    @Test
    void connectionPoolDefaultsToVerifyFullWhenSslModeIsNotConfigured() {
        assertEquals("verify-full", ConnectionPool.resolveSslMode("db.example.com", null));
        assertEquals("jdbc:postgresql://db.example.com:5432/app?sslmode=verify-full",
                ConnectionPool.buildJdbcUrl("db.example.com", "5432", "app", "verify-full"));
    }

    @Test
    void connectionPoolAllowsDisableOnlyForLocalHosts() {
        assertEquals("disable", ConnectionPool.resolveSslMode("localhost", "disable"));
        assertEquals("disable", ConnectionPool.resolveSslMode("127.0.0.1", "disable"));

        assertThrows(IllegalArgumentException.class,
                () -> ConnectionPool.resolveSslMode("db.example.com", "disable"));
    }

    @Test
    void simpleConnectionUsesSslModeAndRejectsRemotePlaintext() {
        assertEquals("jdbc:postgresql://db.example.com:5432/app?sslmode=verify-full",
                SimpleConnection.buildJdbcUrl("db.example.com", 5432, "app"));
        assertEquals("jdbc:postgresql://localhost:5432/app?sslmode=disable",
                SimpleConnection.buildJdbcUrl("localhost", 5432, "app", false));

        assertThrows(IllegalArgumentException.class,
                () -> SimpleConnection.buildJdbcUrl("db.example.com", 5432, "app", false));

        assertEquals("jdbc:postgresql://db.example.com:5432/app?sslmode=disable",
                SimpleConnection.buildJdbcUrl("db.example.com", 5432, "app", false, true));
    }
}
