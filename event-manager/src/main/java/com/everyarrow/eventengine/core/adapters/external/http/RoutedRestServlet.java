package com.everyarrow.eventengine.core.adapters.external.http;

import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.eventengine.core.routing.ServiceCallFactory;
import com.everyarrow.eventengine.core.services.registry.RouteMatch;
import com.everyarrow.eventengine.core.services.registry.ServiceRegistry;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.everyarrow.eventengine.spi.model.ServiceCallResponse;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class RoutedRestServlet extends HttpServlet {

    private static volatile Engine engine;
    private static final Gson gson = new Gson();
    private static final Logger logger = LoggerFactory.getLogger(RoutedRestServlet.class);

    public static void setEngine(Engine e) {
        if (e == null) {
            throw new IllegalArgumentException("Engine must not be null");
        }
        engine = e;
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Engine e = engine;

        resp.setContentType("application/json");
        if (JettyRestUtils.rejectDisallowedOrigin(req, resp)) {
            return;
        }

        // Handle CORS preflight immediately — no routing needed
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            resp.setStatus(204);
            return;
        }

        if (e == null) {
            resp.setStatus(500);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Engine not initialized");
            resp.getWriter().write(gson.toJson(error));
            return;
        }

        String method = req.getMethod();
        String path = normalizePath(req.getRequestURI());

        logger.debug("[REST] {} {}", method, path);

        ServiceRegistry registry = engine.getRegistry();
        RouteMatch match = registry.findRestRouteMatch(method, path);

        if (match == null) {
            logger.warn("[REST] No route for {} {}", method, path);
            resp.setStatus(404);
            JsonObject error = new JsonObject();
            error.addProperty("error", "No REST route");
            resp.getWriter().write(gson.toJson(error));
            return;
        }

        ServiceCallDefinition def = match.definition();
        String serviceType = def.getServiceType();
        logger.debug("[REST] routed {} {} → serviceType={}", method, path, serviceType);

        try {
            var call = ServiceCallFactory.fromRest(req, serviceType, match.pathParams());

            logger.debug("[REST] executing call type={} paramKeys={}", call.getType(), paramKeys(call.getParams()));

            ServiceCallResponse result = e.execute(call);

            resp.setStatus(result.getHttpStatus());
            // Return the handler's body directly — the UI expects the payload
            // without the ServiceCallResponse envelope (serviceType, durationMs, status).
            // Supports both JsonObject and JsonArray bodies.
            var body = result.getBody();
            String responseJson = body != null ? gson.toJson(body) : "{}";
            logger.debug("[REST] execute complete status={} body-type={} len={}",
                    result.getHttpStatus(),
                    body != null ? body.getClass().getSimpleName() : "null",
                    responseJson.length());
            resp.getWriter().write(responseJson);
            resp.getWriter().flush();
            logger.debug("[REST] response written ({} bytes)", responseJson.length());

        } catch (RequestBodyTooLargeException ex) {
            writeError(resp, 413, "Request body too large");
        } catch (ServiceCallFactory.InvalidJsonBodyException ex) {
            writeError(resp, 400, "Invalid JSON request body");
        } catch (Exception ex) {
            logger.warn("[REST] request failed", ex);
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
        if (p == null || p.isBlank()) return "/";
        p = p.trim();
        if (!p.startsWith("/")) p = "/" + p;
        if (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    private String paramKeys(JsonObject params) {
        if (params == null || params.size() == 0) {
            return "[]";
        }
        return params.keySet().toString();
    }
}
