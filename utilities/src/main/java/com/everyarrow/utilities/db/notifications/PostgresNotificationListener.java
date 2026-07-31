/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.db.notifications;

import org.postgresql.PGConnection;
import org.postgresql.PGNotification;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class PostgresNotificationListener implements AutoCloseable {

    private final PostgresNotificationConfig config;
    private final Supplier<Connection> connectionSupplier;
    private final PostgresNotificationHandler handler;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile Connection activeConnection;
    private volatile Thread worker;

    public PostgresNotificationListener(PostgresNotificationConfig config,
                                        Supplier<Connection> connectionSupplier,
                                        PostgresNotificationHandler handler) {
        this.config = Objects.requireNonNull(config, "config is required");
        this.connectionSupplier = Objects.requireNonNull(connectionSupplier, "connectionSupplier is required");
        this.handler = Objects.requireNonNull(handler, "handler is required");
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        worker = new Thread(this::runLoop, "postgres-notification-" + config.channel());
        worker.setDaemon(true);
        worker.start();
    }

    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void close() {
        running.set(false);
        closeActiveConnection();
        Thread currentWorker = worker;
        if (currentWorker != null) {
            currentWorker.interrupt();
        }
    }

    private void runLoop() {
        while (running.get()) {
            try (Connection connection = connectionSupplier.get()) {
                activeConnection = connection;
                listen(connection);
                poll(connection.unwrap(PGConnection.class));
            } catch (Exception ex) {
                if (running.get()) {
                    sleep(config.reconnectDelay().toMillis());
                }
            } finally {
                activeConnection = null;
            }
        }
    }

    private void listen(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("LISTEN " + config.channel());
        }
    }

    private void poll(PGConnection pgConnection) throws Exception {
        int timeoutMillis = Math.toIntExact(config.pollTimeout().toMillis());
        while (running.get()) {
            PGNotification[] notifications = pgConnection.getNotifications(timeoutMillis);
            if (notifications == null) {
                continue;
            }
            for (PGNotification notification : notifications) {
                if (notification != null) {
                    handler.handle(new PostgresNotificationMessage(
                            notification.getName(),
                            notification.getPID(),
                            notification.getParameter()));
                }
            }
        }
    }

    private void closeActiveConnection() {
        Connection connection = activeConnection;
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception ignored) {
            }
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
