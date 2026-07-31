package com.everyarrow.eventengine.core.adapters.external.http.webhook;

import com.everyarrow.eventengine.boot.Engine;

/**
 * Codex: Handles webhook callbacks from external systems.
 */
public class WebhookAdapter {
    private final Engine engine;

    public WebhookAdapter(Engine engine) { this.engine = engine; }

    public void start(Engine engine) throws Exception {
        // JettyBootstrap wires WebhookServlet, which performs request verification.
    }
}
