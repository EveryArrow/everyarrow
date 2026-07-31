/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.spi.services;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * Engine-owned realtime transport available to background plugins.
 */
public interface RealtimeGateway {

    void addConnectionListener(Consumer<RealtimeConnection> listener);

    void removeConnectionListener(Consumer<RealtimeConnection> listener);

    void broadcast(String room, String event, Object... payload);

    Collection<RealtimeConnection> connectionsInRoom(String room);
}
