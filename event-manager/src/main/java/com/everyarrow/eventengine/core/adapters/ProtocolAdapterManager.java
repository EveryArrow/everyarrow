package com.everyarrow.eventengine.core.adapters;

import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.eventengine.core.adapters.internal.ArtemisQueueAdapter;
import com.everyarrow.eventengine.core.adapters.internal.SchedulerAdapter;
import com.everyarrow.eventengine.core.services.runtime.PostgresNotificationAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages non-HTTP protocol adapters owned by the engine process.
 */
public final class ProtocolAdapterManager {

    private static final List<ProtocolAdapter> ADAPTERS = List.of(
            new SchedulerAdapter(),
            new ArtemisQueueAdapter(),
            new PostgresNotificationAdapter());

    private ProtocolAdapterManager() {
    }

    public static List<ProtocolAdapter> getAdapters() {
        return ADAPTERS;
    }

    public static void startAll(Engine engine) {
        List<ProtocolAdapter> started = new ArrayList<>();
        for (ProtocolAdapter adapter : ADAPTERS) {
            try {
                adapter.start(engine);
                started.add(adapter);
            } catch (RuntimeException e) {
                stopStarted(started);
                throw e;
            } catch (Exception e) {
                stopStarted(started);
                throw new RuntimeException("Failed to start protocol adapter " + adapter.getClass().getName(), e);
            }
        }
    }

    public static void stopAll() {
        for (ProtocolAdapter adapter : ADAPTERS) {
            try {
                adapter.stop();
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }
        }
    }

    private static void stopStarted(List<ProtocolAdapter> started) {
        List<ProtocolAdapter> reversed = new ArrayList<>(started);
        Collections.reverse(reversed);
        for (ProtocolAdapter adapter : reversed) {
            try {
                adapter.stop();
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }
        }
    }
}
