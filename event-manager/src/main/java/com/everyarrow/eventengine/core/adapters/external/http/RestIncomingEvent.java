package com.everyarrow.eventengine.core.adapters.external.http;

import com.everyarrow.eventengine.core.adapters.IncomingEvent;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * IncomingEvent implementation for REST/HTTP.
 *
 * This class simply wraps a MutableHttpRequest and exposes:
 *   • the body
 *   • request metadata (headers, path, querystring, method)
 *   • timestamp
 *   • transport = "REST"
 *
 * It contains ZERO routing logic, ZERO mapping logic.
 * All routing decisions happen in ServiceCallMapper.
 */
public class RestIncomingEvent implements IncomingEvent {

    private final MutableHttpRequest request;
    private final Instant timestamp = Instant.now();

    public RestIncomingEvent(MutableHttpRequest request) {
        this.request = request;
    }

    @Override
    public String getBody() {
        return request.getBodyAsString();
    }

    @Override
    public Map<String, String> getMetadata() {
        Map<String, String> meta = new HashMap<>();

        HttpServletRequest raw = request.getOriginalRequest();

        // Collect headers
        var headerNames = raw.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                meta.put("header:" + name.toLowerCase(), raw.getHeader(name));
            }
        }

        // Basic REST transport metadata
        meta.put("method", raw.getMethod());
        meta.put("path", raw.getRequestURI());
        meta.put("query", raw.getQueryString() == null ? "" : raw.getQueryString());
        meta.put("contentType", raw.getContentType() == null ? "" : raw.getContentType());
        meta.put("encoding", raw.getCharacterEncoding() == null ? "UTF-8" : raw.getCharacterEncoding());

        return Collections.unmodifiableMap(meta);
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String getTransport() {
        return "REST";
    }

    /** Adapter helper for advanced use cases (not used by engine). */
    public MutableHttpRequest getRequest() {
        return request;
    }
}
