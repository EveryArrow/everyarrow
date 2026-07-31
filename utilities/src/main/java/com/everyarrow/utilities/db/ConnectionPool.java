/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.db;

import com.everyarrow.utilities.environment.Environment;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BACKWARD-COMPATIBLE PostgreSQL SimpleConnection Pool
 * -----------------------------------------------
 * - Maintains original single-database behavior
 * - Adds NEW multi-database support without breaking anything
 */
public final class ConnectionPool {

    /** Legacy, global default pool (existing apps use this) */
    private static volatile HikariDataSource defaultDataSource;

    /** New: pool per database name */
    private static final Map<String, HikariDataSource> MULTI_POOLS = new ConcurrentHashMap<>();

    private ConnectionPool() { }

    // ---------------------------------------------------------------------
    // BACKWARD-COMPATIBLE DEFAULT POOL
    // ---------------------------------------------------------------------

    private static HikariDataSource getDefaultDataSource() {
        if (defaultDataSource == null) {
            synchronized (ConnectionPool.class) {
                if (defaultDataSource == null) {
                    defaultDataSource = createPoolFor(
                            Environment.getInstance().get("DB_NAME")
                    );
                }
            }
        }
        return defaultDataSource;
    }

    /**
     * Legacy method used by existing applications.
     * Connects to DB_NAME from environment.
     */
    public static Connection getPostgresConnection() throws SQLException {
        return getDefaultDataSource().getConnection();
    }

    /**
     * Legacy alias for default connection.
     * (Existing apps expect this behavior.)
     */
    public static Connection getConnection() throws SQLException {
        return getPostgresConnection();
    }



    // Create a HikariCP pool for a specific database name
    private static HikariDataSource createPoolFor(String dbName) {
        String host = Environment.getInstance().get("DB_HOST");
        String port = Environment.getInstance().get("DB_PORT", "5432");
        String user = Environment.getInstance().get("DB_USERNAME");
        String pass = Environment.getInstance().get("DB_PASSWORD");
        String sslMode = resolveSslMode(host, Environment.getInstance().get("DB_SSL_MODE"));
        String poolSize = Environment.getInstance().get("DB_POOL_SIZE", "10");

        Objects.requireNonNull(host, "DB_HOST not set");
        Objects.requireNonNull(user, "DB_USERNAME not set");
        Objects.requireNonNull(pass, "DB_PASSWORD not set");

        String jdbcUrl = buildJdbcUrl(host, port, dbName, sslMode);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(user);
        config.setPassword(pass);
        config.setDriverClassName("org.postgresql.Driver");

        config.setMaximumPoolSize(Integer.parseInt(poolSize));
        config.setMinimumIdle(1);
        config.setIdleTimeout(60000);
        config.setConnectionTimeout(30000);
        config.setPoolName("EA-DB-" + dbName);

        return new HikariDataSource(config);
    }
// utilities: ConnectionPool.java

    public static Connection getPostgresConnection(String dbName) throws SQLException {
        String name = (dbName == null || dbName.isBlank())
                ? Environment.getInstance().get("DB_NAME")
                : dbName.trim();

        HikariDataSource ds = MULTI_POOLS.computeIfAbsent(name, ConnectionPool::createPoolFor);
        return ds.getConnection();
    }

    static String buildJdbcUrl(String host, String port, String dbName, String sslMode) {
        return String.format(
                "jdbc:postgresql://%s:%s/%s?sslmode=%s",
                host, port, dbName, sslMode
        );
    }

    static String resolveSslMode(String host, String configuredSslMode) {
        if (configuredSslMode == null || configuredSslMode.isBlank()) {
            return "verify-full";
        }

        String sslMode = configuredSslMode.trim();
        if ("disable".equalsIgnoreCase(sslMode) && !isLocalHost(host)) {
            throw new IllegalArgumentException("DB_SSL_MODE=disable is allowed only for localhost or loopback hosts.");
        }
        return sslMode;
    }

    static boolean isLocalHost(String host) {
        if (host == null) {
            return false;
        }
        String normalized = host.trim().toLowerCase();
        return "localhost".equals(normalized)
                || "127.0.0.1".equals(normalized)
                || "::1".equals(normalized)
                || normalized.startsWith("127.");
    }

    // ---------------------------------------------------------------------
    // SHUTDOWN
    // ---------------------------------------------------------------------

    public static void closeAll() {
        if (defaultDataSource != null) {
            try { defaultDataSource.close(); } catch (Exception ignored) {}
            defaultDataSource = null;
        }

        for (var ds : MULTI_POOLS.values()) {
            try { ds.close(); } catch (Exception ignored) {}
        }

        MULTI_POOLS.clear();
    }
}
