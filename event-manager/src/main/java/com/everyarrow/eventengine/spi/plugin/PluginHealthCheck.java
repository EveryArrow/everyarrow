/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.spi.plugin;

/**
 * Optional plugin-owned health contract.
 */
public interface PluginHealthCheck {

    PluginHealthResult health(EnginePluginContext ctx) throws Exception;
}
