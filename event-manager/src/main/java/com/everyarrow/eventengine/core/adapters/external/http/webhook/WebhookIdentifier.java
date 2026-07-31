package com.everyarrow.eventengine.core.adapters.external.http.webhook;

import com.google.gson.JsonObject;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Interface that defines the contract for handling webhooks from a specific provider.
 * Each webhook provider (for example, Stripe or GitHub) should implement this interface to provide
 * vendor-specific verification and event type extraction logic.
 */
public interface WebhookIdentifier {

    /**
     * Verifies the authenticity of the webhook request.
     * This method should implement the vendor-specific signature verification logic.
     *
     * @param requestBody the request body as a String.
     * @param request     the HttpServletRequest object containing the request details.
     * @throws SecurityException if the verification fails.
     */
    void verifySignature(String requestBody, HttpServletRequest request) throws SecurityException;

    /**
     * Extracts the event type from the webhook request.
     * This method should implement the vendor-specific logic to determine the event type
     * from the request body or headers.
     *
     * @param requestBody the JSON object representing the request body.
     * @param request     the HttpServletRequest object containing the request details.
     * @return a String representing the event type, or null if the event type cannot be determined.
     */
    String getEventType(JsonObject requestBody, HttpServletRequest request);

    /**
     * Processes a webhook by dispatching the event to the appropriate processor.
     * This method should be overridden by concrete implementations to provide the logic
     * for dispatching the event to the appropriate processor.
     *
     * @param eventType the type of the webhook event
     * @param payload   the webhook payload as a JsonObject
     */
    void process(String eventType, JsonObject payload);
}
