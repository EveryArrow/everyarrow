package com.everyarrow.eventengine.core.adapters.external.http;

import com.everyarrow.eventengine.core.services.runtime.ServiceCall;
import com.everyarrow.eventengine.spi.handler.ServiceCallHandlerException;
import com.everyarrow.utilities.environment.Environment;
import com.everyarrow.utilities.json.JsonUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class JettyRestUtils {

    private static final String DEFAULT_ALLOWED_METHODS = "GET, POST, PUT, PATCH, DELETE, OPTIONS";
    private static final Map<String, String> ALLOWED_CORS_HEADERS = allowedCorsHeaders();
    private static final String DEFAULT_ALLOWED_HEADERS = String.join(", ", ALLOWED_CORS_HEADERS.values());
    private static final CorsPolicyResolver CORS_POLICY_RESOLVER = new CorsPolicyResolver();

    private JettyRestUtils() { /* no instantiation */ }

    public static boolean rejectDisallowedOrigin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank()) {
            return false;
        }

        if (isOriginAllowed(origin)) {
            applyCorsHeaders(request, response, origin);
            return false;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");

        JsonObject error = new JsonObject();
        error.addProperty("error", "Origin not allowed");
        response.getWriter().write(JsonUtils.getGson().toJson(error));
        return true;
    }

    public static boolean isOriginAllowed(String origin) {
        return CORS_POLICY_RESOLVER.isOriginAllowed(origin);
    }

    public static void allowCORS(HttpServletResponse response) {
        response.setHeader("Vary", "Origin");
        response.setHeader("Access-Control-Allow-Methods", DEFAULT_ALLOWED_METHODS);
        response.setHeader("Access-Control-Allow-Headers", DEFAULT_ALLOWED_HEADERS);
        response.setHeader("Access-Control-Max-Age", "3600");
    }

    private static void applyCorsHeaders(HttpServletRequest request, HttpServletResponse response, String origin) {
        response.setHeader("Vary", "Origin");
        response.setHeader("Access-Control-Allow-Origin", origin);
        response.setHeader("Access-Control-Allow-Methods", DEFAULT_ALLOWED_METHODS);

        String requestedHeaders = normalize(request.getHeader("Access-Control-Request-Headers"));
        response.setHeader(
                "Access-Control-Allow-Headers",
                requestedHeaders.isEmpty() ? DEFAULT_ALLOWED_HEADERS : allowedRequestedHeaders(requestedHeaders)
        );
        response.setHeader("Access-Control-Max-Age", "3600");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String allowedRequestedHeaders(String requestedHeaders) {
        return Arrays.stream(requestedHeaders.split(","))
                .map(String::trim)
                .map(header -> ALLOWED_CORS_HEADERS.get(header.toLowerCase(Locale.ROOT)))
                .filter(header -> header != null)
                .distinct()
                .collect(Collectors.joining(", "));
    }

    private static Map<String, String> allowedCorsHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        addAllowedHeader(headers, "Content-Type");
        addAllowedHeader(headers, "Authorization");
        addAllowedHeader(headers, "X-Requested-With");
        addAllowedHeader(headers, "X-API-Key");
        addAllowedHeader(headers, "X-EveryArrow-Api-Key");
        addAllowedHeader(headers, "X-EveryArrow-Authorization");
        addAllowedHeader(headers, "X-EveryArrow-Customer-Id");
        addAllowedHeader(headers, "ea_perch_job_group_execution_instance_id");
        addAllowedHeader(headers, "ea_perch_job_execution_instance_id");
        addAllowedHeader(headers, "ea_perch_task_execution_instance_id");
        addAllowedHeader(headers, "perch.jobGroupExecutionInstanceId");
        addAllowedHeader(headers, "perch.jobExecutionInstanceId");
        addAllowedHeader(headers, "perch.taskExecutionInstanceId");
        return Collections.unmodifiableMap(headers);
    }

    private static void addAllowedHeader(Map<String, String> headers, String header) {
        headers.put(header.toLowerCase(Locale.ROOT), header);
    }

    public static ServiceCall getServiceCall(String inputRequest) {
        ServiceCall serviceCall;
        try {
            serviceCall = JsonUtils.getGson().fromJson(inputRequest, ServiceCall.class);
        } catch (Exception exception) {
            throw new ServiceCallHandlerException("Unsupported service call.", exception);
        }

        if (serviceCall.getType() == null) {
            throw new ServiceCallHandlerException("Unsupported service call.");
        }

        return serviceCall;
    }

    public static ServiceCall getServiceCall(String serviceCallType, String inputRequest) {
        if (serviceCallType == null) {
            throw new ServiceCallHandlerException("Unsupported service call. " + serviceCallType);
        }

        ServiceCall serviceCall = new ServiceCall();
        serviceCall.setType(serviceCallType);
        try {
            JsonObject params = JsonUtils.getGson().fromJson(inputRequest, JsonObject.class);
            serviceCall.setParams(params);
        } catch (Exception exception) {
            throw new ServiceCallHandlerException("Unsupported service call.", exception);
        }

        if (serviceCall.getType() == null) {
            throw new ServiceCallHandlerException("Unsupported service call.");
        }

        return serviceCall;
    }

    public static ServiceCall getServiceCall(InputStream inputStream) throws IOException {
        JsonObject inputObject = JsonUtils.getGson().fromJson(
                new String(HttpRequestBodyReader.read(inputStream), StandardCharsets.UTF_8), JsonObject.class);
        ServiceCall serviceCall;
        try {
            serviceCall = JsonUtils.getGson().fromJson(inputObject.get("body").getAsString(), ServiceCall.class);
        } catch (Exception exception) {
            throw new ServiceCallHandlerException("Unsupported service call.", exception);
        }

        if (serviceCall.getType() == null) {
            throw new ServiceCallHandlerException("Unsupported service call.");
        }

        return serviceCall;
    }

    public static ServiceCall getServiceCall(String serviceCallType, InputStream inputStream) throws IOException {
        if (serviceCallType == null) {
            throw new ServiceCallHandlerException("Unsupported service call. " + serviceCallType);
        }

        JsonObject inputObject = JsonUtils.getGson().fromJson(
                new String(HttpRequestBodyReader.read(inputStream), StandardCharsets.UTF_8), JsonObject.class);
        ServiceCall serviceCall = new ServiceCall();
        serviceCall.setType(serviceCallType);
        try {
            JsonObject params = JsonUtils.getGson().fromJson(inputObject.get("body").getAsString(), JsonObject.class);
            serviceCall.setParams(params);
        } catch (Exception exception) {
            throw new ServiceCallHandlerException("Unsupported service call.", exception);
        }

        return serviceCall;
    }

    public static JsonObject extractQueryParams(HttpServletRequest request) {
        JsonObject json = new JsonObject();
        Map<String, String[]> paramMap = request.getParameterMap();

        for (Map.Entry<String, String[]> entry : paramMap.entrySet()) {
            String name = entry.getKey();
            String[] values = entry.getValue();

            if (values == null || values.length == 0) {
                // no value? add empty string
                json.addProperty(name, "");
            } else if (values.length == 1) {
                // single value → primitive
                json.addProperty(name, values[0]);
            } else {
                // multiple values → array
                JsonArray arr = new JsonArray();
                for (String v : values) {
                    arr.add(v);
                }
                json.add(name, arr);
            }
        }

        return json;
    }
}
