package com.everyarrow.eventengine.core.adapters.external.http.webhook;

import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.eventengine.core.adapters.external.http.JettyRestUtils;
import com.everyarrow.eventengine.core.adapters.external.http.MutableHttpRequest;
import com.everyarrow.eventengine.core.adapters.external.http.RequestBodyTooLargeException;
import com.everyarrow.eventengine.core.routing.ServiceCallFactory;
import com.everyarrow.eventengine.core.services.registry.ServiceRegistry;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.everyarrow.eventengine.spi.model.ServiceCallResponse;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Verifies webhook request integrity before forwarding authenticated metadata
 * into the standard ServiceCall security pipeline.
 */
public class WebhookServlet extends HttpServlet {

    private static volatile Engine engine;
    private static final Gson gson = new Gson();

    public static void setEngine(Engine e) {
        if (e == null) {
            throw new IllegalArgumentException("Engine must not be null");
        }
        engine = e;
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Engine e = engine;

        if (JettyRestUtils.rejectDisallowedOrigin(req, resp)) {
            return;
        }

        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            resp.setStatus(204);
            return;
        }

        resp.setContentType("application/json");

        if (e == null) {
            writeError(resp, 500, "Engine not initialized");
            return;
        }

        String path = normalizePath(req.getRequestURI());

        ServiceRegistry registry = engine.getRegistry();
        ServiceCallDefinition match = registry.findWebhook(path);

        if (match == null) {
            writeError(resp, 404, "No webhook route");
            return;
        }

        String serviceType = match.getServiceType();

        try {
            MutableHttpRequest verifiedRequest = new MutableHttpRequest(req);
            WebhookRequestVerifier.Verification verification = WebhookRequestVerifier.verify(
                    verifiedRequest,
                    serviceType,
                    verifiedRequest.getBodyAsString());
            if (!verification.accepted()) {
                writeError(resp, verification.status(), verification.error());
                return;
            }

            var call = ServiceCallFactory.fromWebhook(verifiedRequest, serviceType, verification.secretSource());
            ServiceCallResponse result = e.execute(call);

            resp.setStatus(result.getHttpStatus());
            resp.getWriter().write(result.toJson());

        } catch (RequestBodyTooLargeException ex) {
            writeError(resp, 413, "Request body too large");
        } catch (ServiceCallFactory.InvalidJsonBodyException ex) {
            writeError(resp, 400, "Invalid JSON request body");
        } catch (Exception ex) {
            writeError(resp, 500, "Internal server error");
        }
    }

    private void writeError(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        resp.getWriter().write(gson.toJson(error));
    }

    private String normalizePath(String p) {
        if (p == null || p.isBlank()) {
            return "/";
        }
        p = p.trim();
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        if (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }
}
