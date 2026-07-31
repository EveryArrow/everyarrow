/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.spi.services;

/**
 * Cross-plugin service interface for streaming pipeline output to the browser.
 *
 * <p>The {@code everyarrow-console} plugin registers an implementation of this
 * interface during its {@code onStart()}.  The {@code dittoe-swarm-controller}
 * plugin resolves it from {@link com.everyarrow.eventengine.spi.plugin.EnginePluginContext}
 * and calls it instead of making an HTTP request to port 4560.
 *
 * <p>If the console plugin is not deployed (headless mode), callers receive
 * {@code null} from {@code getService(ConsoleLogEmitter.class)} and must fall
 * back to their original transport or a no-op.
 */
public interface ConsoleLogEmitter {

    /**
     * Emit a single log line for a pipeline execution.
     *
     * @param pipelineExecutionId unique execution identifier
     * @param line                the log line (no trailing newline required)
     */
    void emitLog(String pipelineExecutionId, String line);

    /**
     * Emit a status update for a pipeline execution.
     *
     * @param pipelineExecutionId unique execution identifier
     * @param status              new status string (e.g. "RUNNING", "COMPLETED", "FAILED")
     */
    void emitStatus(String pipelineExecutionId, String status);
}
