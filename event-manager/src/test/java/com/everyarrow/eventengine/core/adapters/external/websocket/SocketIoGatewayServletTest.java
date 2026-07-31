package com.everyarrow.eventengine.core.adapters.external.websocket;

import com.everyarrow.utilities.environment.Environment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocketIoGatewayServletTest {

    @AfterEach
    void resetEnvironment() {
        Environment.reset();
    }

    @Test
    void usesConservativeResourceDefaults() {
        assertEquals(
                64 * 1024,
                SocketIoGatewayServlet.positiveIntConfig(
                        SocketIoGatewayServlet.MAX_TEXT_MESSAGE_BYTES_CONFIG, 64 * 1024));
        assertEquals(
                60,
                SocketIoGatewayServlet.positiveLongConfig(
                        SocketIoGatewayServlet.IDLE_TIMEOUT_SECONDS_CONFIG, 60));
        assertEquals(
                500,
                SocketIoGatewayServlet.positiveIntConfig(
                        SocketIoGatewayServlet.MAX_CONNECTIONS_CONFIG, 500));
        assertEquals(
                120,
                SocketIoGatewayServlet.positiveIntConfig(
                        SocketIoGatewayServlet.MAX_EVENTS_PER_MINUTE_CONFIG, 120));
        assertEquals(
                32,
                SocketIoGatewayServlet.positiveIntConfig(
                        SocketIoGatewayServlet.MAX_ROOMS_PER_CONNECTION_CONFIG, 32));
    }

    @Test
    void acceptsPositiveOverridesAndRejectsInvalidValues() {
        Environment.put(SocketIoGatewayServlet.MAX_CONNECTIONS_CONFIG, "25");
        Environment.put(SocketIoGatewayServlet.MAX_EVENTS_PER_MINUTE_CONFIG, "-1");
        Environment.put(SocketIoGatewayServlet.TOKEN_REVALIDATION_SECONDS_CONFIG, "invalid");

        assertEquals(
                25,
                SocketIoGatewayServlet.positiveIntConfig(
                        SocketIoGatewayServlet.MAX_CONNECTIONS_CONFIG, 500));
        assertEquals(
                120,
                SocketIoGatewayServlet.positiveIntConfig(
                        SocketIoGatewayServlet.MAX_EVENTS_PER_MINUTE_CONFIG, 120));
        assertEquals(
                30,
                SocketIoGatewayServlet.positiveLongConfig(
                        SocketIoGatewayServlet.TOKEN_REVALIDATION_SECONDS_CONFIG, 30));
    }

    @Test
    void readsBearerTokenOnlyFromStructuredAuthPayload() {
        assertEquals("token-value", SocketIoGatewayServlet.bearerToken(Map.of("token", " token-value ")));
        assertEquals("json-token", SocketIoGatewayServlet.bearerToken("{\"token\":\"json-token\"}"));

        assertThrows(
                IllegalArgumentException.class,
                () -> SocketIoGatewayServlet.bearerToken("token-without-object"));
        assertThrows(
                IllegalArgumentException.class,
                () -> SocketIoGatewayServlet.bearerToken(Map.of("other", "value")));
    }

    @Test
    void handshakeRequiresWebSocketTransportAndAllowedOrigin() {
        Environment.put("EA_ALLOWED_CORS_ORIGINS", "https://console.everyarrow.io");

        assertTrue(SocketIoGatewayServlet.isAllowedHandshake(
                Map.of("transport", "websocket"),
                Map.of("Origin", List.of("https://console.everyarrow.io"))));
        assertTrue(SocketIoGatewayServlet.isAllowedHandshake(
                Map.of("transport", "websocket"),
                Map.of("Origin", List.of("http://localhost:5173"))));
        assertFalse(SocketIoGatewayServlet.isAllowedHandshake(
                Map.of("transport", "polling"),
                Map.of("Origin", List.of("https://console.everyarrow.io"))));
        assertFalse(SocketIoGatewayServlet.isAllowedHandshake(
                Map.of("transport", "websocket"),
                Map.of("Origin", List.of("https://attacker.example"))));
    }
}
