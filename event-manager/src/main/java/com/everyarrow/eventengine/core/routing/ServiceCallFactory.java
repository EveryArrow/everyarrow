package com.everyarrow.eventengine.core.routing;

import com.everyarrow.eventengine.core.adapters.external.http.HttpRequestBodyReader;
import com.everyarrow.eventengine.core.services.runtime.ServiceCall;
import com.everyarrow.eventengine.core.services.runtime.ServiceCallSource;
import com.everyarrow.eventengine.core.services.runtime.PerchRuntimeMetadata;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Builds ServiceCall objects from inbound HTTP requests.
 *
 * This version merges:
 *  - JSON body (if present)
 *  - query params
 *  - extracted route template params (e.g. {id})
 *
 * Precedence:
 *   route params override query params override JSON body fields
 */
public final class ServiceCallFactory {

    private static final Gson gson = new Gson();
    public static final String RAW_ARRAY_BODY_KEY = "__ea_raw_array_body__";
    private static final String CUSTOM_AUTH_HEADER = "X-EveryArrow-Authorization";
    private static final String CUSTOM_API_KEY_HEADER = "X-EveryArrow-Api-Key";
    private static final String CUSTOM_CUSTOMER_ID_HEADER = "X-EveryArrow-Customer-Id";

    private ServiceCallFactory() {
    }

    public static class InvalidJsonBodyException extends IOException {
        public InvalidJsonBodyException(String message, Throwable cause) {
            super(message, cause);
        }

        public InvalidJsonBodyException(String message) {
            super(message);
        }
    }

    public static ServiceCall fromRest(HttpServletRequest req,
                                       String serviceType) throws IOException {
        return fromRest(req, serviceType, Map.of());
    }

    public static ServiceCall fromRest(HttpServletRequest req,
                                       String serviceType,
                                       Map<String, String> pathParams) throws IOException {

        JsonObject body = readJsonBody(req);
        JsonObject merged = mergeParams(body, req.getQueryString(), pathParams);

        ServiceCall call = new ServiceCall();
        call.setType(serviceType);
        call.setParams(merged);
        call.setSource(ServiceCallSource.REST);
        call.putMetadata("http.method", req.getMethod());
        call.putMetadata("http.path", req.getRequestURI());
        putIfPresent(call, "http.authorization",
            firstNonBlank(req.getHeader("Authorization"), req.getHeader(CUSTOM_AUTH_HEADER)));
        putIfPresent(call, "http.x-api-key",
            firstNonBlank(req.getHeader("x-api-key"), req.getHeader(CUSTOM_API_KEY_HEADER)));
        putIfPresent(call, "http.customer-id", req.getHeader(CUSTOM_CUSTOMER_ID_HEADER));
        PerchRuntimeMetadata.normalizeFromParams(call);
        PerchRuntimeMetadata.normalizeFromHttpHeaders(call, req::getHeader);

        return call;
    }

    public static ServiceCall fromWebhook(HttpServletRequest req,
                                          String serviceType) throws IOException {
        return fromWebhook(req, serviceType, null);
    }

    public static ServiceCall fromWebhook(HttpServletRequest req,
                                          String serviceType,
                                          String secretSource) throws IOException {

        JsonObject body = readJsonBody(req);

        ServiceCall call = new ServiceCall();
        call.setType(serviceType);
        call.setParams(body);
        call.setSource(ServiceCallSource.WEBHOOK);
        call.putMetadata("http.path", req.getRequestURI());
        putIfPresent(call, "http.authorization",
            firstNonBlank(req.getHeader("Authorization"), req.getHeader(CUSTOM_AUTH_HEADER)));
        putIfPresent(call, "http.x-api-key",
            firstNonBlank(req.getHeader("x-api-key"), req.getHeader(CUSTOM_API_KEY_HEADER)));
        putIfPresent(call, "http.customer-id", req.getHeader(CUSTOM_CUSTOMER_ID_HEADER));
        PerchRuntimeMetadata.normalizeFromParams(call);
        PerchRuntimeMetadata.normalizeFromHttpHeaders(call, req::getHeader);

        if (secretSource != null) {
            call.putMetadata("webhook.secretSource", secretSource);
        }

        return call;
    }

    public static ServiceCall fromWebSocket(JsonObject payload,
                                            String serviceType,
                                            String path) {
        return fromWebSocket(payload, serviceType, path, Map.of());
        }

        public static ServiceCall fromWebSocket(JsonObject payload,
                            String serviceType,
                            String path,
                            Map<String, Object> metadata) {
        JsonObject safePayload = payload == null ? new JsonObject() : payload.deepCopy();
        JsonObject params = extractWebSocketParams(safePayload);

        if (safePayload.has("correlationId")
                && safePayload.get("correlationId").isJsonPrimitive()
                && !params.has("correlationId")) {
            params.add("correlationId", safePayload.get("correlationId"));
        }

        ServiceCall call = new ServiceCall();
        call.setType(serviceType);
        call.setParams(params);
        call.setSource(ServiceCallSource.WEBSOCKET);
        call.putMetadata("websocket.path", path);
        PerchRuntimeMetadata.copyFromPayload(call, safePayload);
        PerchRuntimeMetadata.normalizeFromParams(call);

        if (metadata != null) {
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                call.putMetadata(entry.getKey(), entry.getValue());
            }
        }

        if (safePayload.has("correlationId") && safePayload.get("correlationId").isJsonPrimitive()) {
            call.putMetadata("websocket.correlationId", safePayload.get("correlationId").getAsString());
        }

        return call;
    }

    private static JsonObject mergeParams(JsonObject body,
                                          String queryString,
                                          Map<String, String> pathParams) {
        JsonObject out = (body == null) ? new JsonObject() : body.deepCopy();

        // query params
        Map<String, List<String>> query = parseQuery(queryString);
        for (var e : query.entrySet()) {
            String key = e.getKey();
            List<String> vals = e.getValue();
            if (vals == null || vals.isEmpty()) continue;
            // simple rule: first value wins
            out.addProperty(key, vals.get(0));
        }

        // path params override everything
        if (pathParams != null) {
            for (var e : pathParams.entrySet()) {
                if (e.getKey() == null) continue;
                out.addProperty(e.getKey(), e.getValue());
            }
        }

        return out;
    }

    private static void putIfPresent(ServiceCall call, String key, String value) {
        if (call != null && key != null && value != null && !value.isBlank()) {
            call.putMetadata(key, value);
        }
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private static Map<String, List<String>> parseQuery(String queryString) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        if (queryString == null || queryString.isBlank()) return out;

        String qs = queryString;
        if (qs.startsWith("?")) qs = qs.substring(1);

        for (String part : qs.split("&")) {
            if (part.isBlank()) continue;
            int idx = part.indexOf('=');
            String k = idx >= 0 ? part.substring(0, idx) : part;
            String v = idx >= 0 ? part.substring(idx + 1) : "";

            k = urlDecode(k);
            v = urlDecode(v);

            out.computeIfAbsent(k, kk -> new ArrayList<>()).add(v);
        }
        return out;
    }

    private static JsonObject extractWebSocketParams(JsonObject payload) {
        if (payload == null) {
            return new JsonObject();
        }
        if (payload.has("params") && payload.get("params").isJsonObject()) {
            return payload.getAsJsonObject("params").deepCopy();
        }
        return payload.deepCopy();
    }

    private static String urlDecode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    private static JsonObject readJsonBody(HttpServletRequest req) throws IOException {
        byte[] bodyBytes = HttpRequestBodyReader.read(req);
        if (bodyBytes.length == 0) {
            return new JsonObject();
        }
        try {
            JsonElement body = gson.fromJson(new String(bodyBytes, StandardCharsets.UTF_8), JsonElement.class);
            if (body == null || body.isJsonNull()) {
                return new JsonObject();
            }
            if (body.isJsonObject()) {
                return body.getAsJsonObject();
            }
            if (body.isJsonArray()) {
                JsonObject wrapped = new JsonObject();
                wrapped.add(RAW_ARRAY_BODY_KEY, body.getAsJsonArray());
                return wrapped;
            }
            throw new InvalidJsonBodyException("JSON request body must be an object or array");
        } catch (Exception e) {
            if (e instanceof InvalidJsonBodyException invalidJsonBodyException) {
                throw invalidJsonBodyException;
            }
            throw new InvalidJsonBodyException("Malformed JSON request body", e);
        }
    }
}
