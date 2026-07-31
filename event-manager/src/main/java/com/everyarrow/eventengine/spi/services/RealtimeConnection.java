/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.spi.services;

import java.util.function.Consumer;

/**
 * Authenticated realtime client connection exposed to plugins.
 */
public interface RealtimeConnection {

    String getId();

    String getAuthProviderId();

    String getEmail();

    void on(String event, Consumer<Object[]> listener);

    void onDisconnect(Runnable listener);

    void send(String event, Object... payload);

    void joinRoom(String room);

    void leaveRoom(String room);

    void disconnect();
}
