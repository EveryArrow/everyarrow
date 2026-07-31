package com.everyarrow.eventengine.core.adapters.external.http;

import com.everyarrow.utilities.environment.Environment;
import jakarta.servlet.http.HttpServletRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class HttpRequestBodyReader {

    static final int DEFAULT_MAX_BODY_BYTES = 2 * 1024 * 1024;
    private static final String MAX_BODY_BYTES_CONFIG = "EA_HTTP_MAX_REQUEST_BODY_BYTES";

    private HttpRequestBodyReader() {
    }

    public static byte[] read(HttpServletRequest request) throws IOException {
        int limit = configuredLimit();
        long declaredLength = request.getContentLengthLong();
        if (declaredLength > limit) {
            throw new RequestBodyTooLargeException(limit);
        }
        return read(request.getInputStream(), limit);
    }

    public static byte[] read(InputStream inputStream) throws IOException {
        return read(inputStream, configuredLimit());
    }

    static byte[] read(InputStream inputStream, int limit) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream(Math.min(limit, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        int bytesRead;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            total += bytesRead;
            if (total > limit) {
                throw new RequestBodyTooLargeException(limit);
            }
            body.write(buffer, 0, bytesRead);
        }

        return body.toByteArray();
    }

    static int configuredLimit() {
        String configured = Environment.getInstance().get(MAX_BODY_BYTES_CONFIG, "");
        if (configured == null || configured.isBlank()) {
            return DEFAULT_MAX_BODY_BYTES;
        }
        try {
            int parsed = Integer.parseInt(configured.trim());
            return parsed > 0 ? parsed : DEFAULT_MAX_BODY_BYTES;
        } catch (NumberFormatException ignored) {
            return DEFAULT_MAX_BODY_BYTES;
        }
    }
}
