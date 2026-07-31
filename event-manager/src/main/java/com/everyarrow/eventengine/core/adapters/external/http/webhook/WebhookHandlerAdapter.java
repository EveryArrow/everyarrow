package com.everyarrow.eventengine.core.adapters.external.http.webhook;

import com.everyarrow.eventengine.core.adapters.external.http.HttpRequestBodyReader;
import com.everyarrow.eventengine.core.adapters.external.http.RequestBodyTooLargeException;
import com.google.gson.JsonParseException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Abstract HttpServlet that serves as the single entry point for all webhook traffic.
 * It orchestrates the process of identifying, verifying, and dispatching webhooks.
 * <p>
 * Concrete implementations should register their WebhookIdentifier instances in the constructor.
 */
public abstract class WebhookHandlerAdapter extends HttpServlet {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebhookHandlerAdapter.class);

    private final Map<String, WebhookIdentifier> identifiers = new HashMap<>();

    /**
     * Constructor that should be overridden by concrete implementations to register
     * their WebhookIdentifier instances.
     */
    public WebhookHandlerAdapter() {
        initializeIdentifiers();
    }

    /**
     * Abstract method that concrete implementations must override to register
     * their WebhookIdentifier instances.
     */
    protected abstract void initializeIdentifiers();

    /**
     * Registers a WebhookIdentifier for a specific URL path.
     *
     * @param urlPath    the URL path (for example, "stripe" or "github")
     * @param identifier the WebhookIdentifier implementation for this URL path
     */
    protected void registerIdentifier(String urlPath, WebhookIdentifier identifier) {
        this.identifiers.put(urlPath, identifier);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.length() < 2) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing vendor in webhook URL.");
            return;
        }

        String vendor = pathInfo.substring(1);
        WebhookIdentifier identifier = identifiers.get(vendor);
        if (identifier == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unsupported webhook vendor: " + vendor);
            return;
        }

        try {
            String requestBody = new String(HttpRequestBodyReader.read(req), StandardCharsets.UTF_8);
            identifier.verifySignature(requestBody, req);

            JsonObject requestBodyObject = JsonParser.parseString(requestBody).getAsJsonObject();
            String eventType = identifier.getEventType(requestBodyObject, req);
            if (eventType != null) {
                identifier.process(eventType, requestBodyObject);
                resp.setStatus(HttpServletResponse.SC_OK);
            } else {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Could not determine event type.");
            }
        } catch (RequestBodyTooLargeException e) {
            resp.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Webhook request body is too large.");
        } catch (JsonParseException | IllegalStateException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Webhook request body must be a JSON object.");
        } catch (SecurityException e) {
            resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Webhook signature verification failed.");
        } catch (Exception e) {
            LOGGER.error("Webhook processing failed for vendor {} ({})", vendor, e.getClass().getSimpleName());
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Webhook processing failed.");
        }
    }
}
