package com.everyarrow.eventengine.core.adapters.external.http;

import com.everyarrow.utilities.environment.Environment;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public final class HttpSecurityFilter extends HttpFilter {
    private static final int TOO_MANY_REQUESTS = 429;
    static final String MAX_REQUESTS_CONFIG = "EA_HTTP_MAX_REQUESTS_PER_MINUTE";
    static final String MAX_CLIENTS_CONFIG = "EA_HTTP_RATE_LIMIT_MAX_CLIENTS";
    static final int DEFAULT_MAX_REQUESTS = 600;
    static final int DEFAULT_MAX_CLIENTS = 10_000;

    private final HttpRequestRateLimiter rateLimiter;

    public HttpSecurityFilter() {
        this(new HttpRequestRateLimiter(
                configuredPositiveInt(MAX_REQUESTS_CONFIG, DEFAULT_MAX_REQUESTS),
                configuredPositiveInt(MAX_CLIENTS_CONFIG, DEFAULT_MAX_CLIENTS)));
    }

    HttpSecurityFilter(HttpRequestRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected void doFilter(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        applySecurityHeaders(request, response);
        HttpRequestRateLimiter.Decision decision =
                rateLimiter.check(request.getRemoteAddr());
        if (!decision.allowed()) {
            response.setStatus(TOO_MANY_REQUESTS);
            response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Too many requests\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    static void applySecurityHeaders(
            HttpServletRequest request,
            HttpServletResponse response) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader(
                "Content-Security-Policy",
                "default-src 'none'; frame-ancestors 'none'; base-uri 'none'");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        response.setHeader("Cache-Control", "no-store");
        if (request.isSecure()) {
            response.setHeader(
                    "Strict-Transport-Security",
                    "max-age=31536000; includeSubDomains");
        }
    }

    static int configuredPositiveInt(String key, int defaultValue) {
        String configured = Environment.getInstance().get(key, "");
        if (configured == null || configured.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(configured.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }
}
