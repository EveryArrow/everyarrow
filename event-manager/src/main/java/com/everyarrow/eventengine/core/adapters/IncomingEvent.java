package com.everyarrow.eventengine.core.adapters;

import java.time.Instant;
import java.util.Map;

/**
 * A transport-agnostic incoming event.
 *
 * This represents any external signal that can trigger a ServiceCall:
 *   • HTTP request
 *   • Queue message
 *   • Webhook
 *   • File watcher event
 *   • Scheduler tick
 *
 * Every adapter must convert its native event into this structure.
 */
public interface IncomingEvent {

    /** Raw event body (JSON, form data, text, queue message body, file content, etc.) */
    String getBody();

    /** Transport metadata (HTTP headers, queue attributes, file info, etc.) */
    Map<String, String> getMetadata();

    /** Timestamp when the adapter received the event */
    Instant getTimestamp();

    /**
     * Transport identifier such as:
     *   "REST"
     *   "QUEUE"
     *   "WEBHOOK"
     *   "FILE"
     *   "SCHEDULER"
     */
    String getTransport();
}
