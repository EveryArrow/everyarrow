/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.spi.plugin;

/**
 * Lifecycle contract for plugins that run background services (queue consumers,
 * schedulers, connection pools) co-located inside the engine JVM.
 *
 * <p>Background plugins do not expose raw transport handlers. They manage
 * internal threads and resources while externally callable behavior remains
 * registered through {@code ServiceCallHandler} definitions. Examples:
 * <ul>
 *   <li>dittoe-swarm-controller (AMQ consumer + Quartz + EC2 fleet)</li>
 * </ul>
 *
 * <p>Declare in {@code plugin.json}:
 * <pre>
 * {
 *   "plugin": {
 *     "id": "my-plugin",
 *     "backgroundClass": "com.example.MyBackgroundPlugin",
 *     "requires": ["some-other-plugin"]
 *   }
 * }
 * </pre>
 *
 * <p>Thread safety: {@code onStart} and {@code onStop} are called from the
 * engine's main thread.  Implementations must not block {@code onStop}.
 */
public interface BackgroundPlugin extends PluginHealthCheck {

    @Override
    default PluginHealthResult health(EnginePluginContext ctx) {
        return PluginHealthResult.up(getClass().getSimpleName());
    }

    /**
     * Called before the engine starts protocol adapters and before {@link #onStart(EnginePluginContext)}.
     * Plugins can register transport-adjacent resources here without opening their own long-running work.
     */
    default void onPreStart(EnginePluginContext ctx) {
        // Optional lifecycle hook.
    }

    /**
     * Called by the engine after all plugins in {@code requires} have started.
     * Start background threads, open connection pools, and subscribe to queues here.
     *
     * @param ctx provides env config and the cross-plugin service locator
     */
    void onStart(EnginePluginContext ctx) throws Exception;

    /**
     * Called by the engine on JVM shutdown (reverse start order).
     * Close threads, pools, and connections.  Must not throw.
     */
    void onStop();
}
