/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.spi.plugin;

/**
 * Context provided to a {@link BackgroundPlugin} at startup.
 *
 * Plugins use this to:
 * <ul>
 *   <li>Read environment variables resolved by the engine (same source as {@code ea.env})</li>
 *   <li>Publish a service implementation for other plugins to consume</li>
 *   <li>Resolve a service published by another plugin that started before this one</li>
 * </ul>
 *
 * Cross-plugin service wiring (e.g. ConsoleLogEmitter) is done through
 * {@link #registerService} / {@link #getService}. The engine guarantees
 * that plugins declared in {@code requires} have already called
 * {@code onStart} before this plugin's {@code onStart} is invoked.
 */
public interface EnginePluginContext {

    /**
     * Returns the value of an environment variable, or {@code null} if absent.
     */
    String get(String key);

    /**
     * Returns the value of an environment variable, falling back to {@code defaultValue}.
     */
    String get(String key, String defaultValue);

    /**
     * Looks up a cross-plugin service by its interface type.
     * Returns {@code null} if no implementation has been registered yet.
     *
     * @param serviceType the interface class used as the lookup key
     */
    <T> T getService(Class<T> serviceType);

    /**
     * Publishes a cross-plugin service implementation.
     * Should be called during {@link BackgroundPlugin#onStart}.
     *
     * @param type the interface class used as the lookup key
     * @param impl the concrete implementation
     */
    <T> void registerService(Class<T> type, T impl);
}
