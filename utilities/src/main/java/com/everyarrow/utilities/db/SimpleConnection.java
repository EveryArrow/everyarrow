/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.db;

import java.sql.DriverManager;
import java.sql.SQLException;

public class SimpleConnection {

    public static java.sql.Connection getConnection(
            String host,
            int port,
            String database,
            String user,
            String password
    ) throws SQLException {
        return DriverManager.getConnection(buildJdbcUrl(host, port, database), user, password);
    }

    // SUBFUNCTION: GET CONNECTION
    public static java.sql.Connection getConnection(
            String host,
            int port,
            String database,
            String user,
            String password,
            boolean useSSL
    ) throws SQLException {
        return DriverManager.getConnection(buildJdbcUrl(host, port, database, useSSL), user, password);
    }

    public static java.sql.Connection getConnection(
            String host,
            int port,
            String database,
            String user,
            String password,
            boolean useSSL,
            boolean allowRemotePlaintext
    ) throws SQLException {
        return DriverManager.getConnection(
                buildJdbcUrl(host, port, database, useSSL, allowRemotePlaintext),
                user,
                password);
    }

    static String buildJdbcUrl(String host, int port, String database) {
        return buildJdbcUrl(host, port, database, "verify-full");
    }

    static String buildJdbcUrl(String host, int port, String database, boolean useSSL) {
        return buildJdbcUrl(host, port, database, useSSL, false);
    }

    static String buildJdbcUrl(String host, int port, String database, boolean useSSL, boolean allowRemotePlaintext) {
        String sslMode = useSSL ? "verify-full" : "disable";
        if (!useSSL && !allowRemotePlaintext && !ConnectionPool.isLocalHost(host)) {
            throw new IllegalArgumentException("Plaintext PostgreSQL connections are allowed only for localhost or loopback hosts.");
        }
        return buildJdbcUrl(host, port, database, sslMode);
    }

    private static String buildJdbcUrl(String host, int port, String database, String sslMode) {
        return String.format(
                "jdbc:postgresql://%s:%d/%s?sslmode=%s",
                host, port, database, sslMode
        );
    }

    // SUBFUNCTION: CLOSE CONNECTION
    public static void closeConnection(java.sql.Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException ignored) {}
        }
    }
}
