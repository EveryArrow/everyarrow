package com.everyarrow.eventengine.core.adapters.external.http;

import com.everyarrow.utilities.environment.Environment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpRequestBodyReaderTest {

    @AfterEach
    void resetEnvironment() {
        Environment.reset();
    }

    @Test
    void readsBodyAtConfiguredLimit() throws Exception {
        Environment.put("EA_HTTP_MAX_REQUEST_BODY_BYTES", "5");
        byte[] expected = "12345".getBytes(StandardCharsets.UTF_8);

        assertArrayEquals(expected, HttpRequestBodyReader.read(new ByteArrayInputStream(expected)));
    }

    @Test
    void rejectsBodyBeyondConfiguredLimit() {
        Environment.put("EA_HTTP_MAX_REQUEST_BODY_BYTES", "5");
        byte[] body = "123456".getBytes(StandardCharsets.UTF_8);

        RequestBodyTooLargeException exception = assertThrows(
                RequestBodyTooLargeException.class,
                () -> HttpRequestBodyReader.read(new ByteArrayInputStream(body)));

        assertEquals(5, exception.getLimitBytes());
    }

    @Test
    void invalidConfigurationFallsBackToDefault() {
        Environment.put("EA_HTTP_MAX_REQUEST_BODY_BYTES", "invalid");

        assertEquals(HttpRequestBodyReader.DEFAULT_MAX_BODY_BYTES, HttpRequestBodyReader.configuredLimit());
    }
}
