package com.everyarrow.eventengine.core.adapters;

import com.everyarrow.eventengine.boot.Engine;
/**
 * A pluggable adapter that binds an external protocol
 * (HTTP, webhook, queue, scheduler, file watcher, etc.)
 * to the Event Engine.
 *
 * Each ProtocolAdapter:
 *   • Starts and listens for its specific external events
 *   • Converts them into IncomingEvent
 *   • Invokes engine.execute()
 *   • Stops gracefully when the engine shuts down
 */
public interface ProtocolAdapter {

    void start(Engine engine);

    void stop();

    default String getName() {
        return this.getClass().getSimpleName();
    }
}
