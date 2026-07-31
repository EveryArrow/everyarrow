package com.everyarrow.eventengine.core.adapters.external.http.webhook;

import com.everyarrow.utilities.environment.Environment;
import jakarta.servlet.http.HttpServletRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class WebhookRequestVerifier {

    private static final String GLOBAL_SECRET_CONFIG = "EA_WEBHOOK_SHARED_SECRET";
    private static final String TIMESTAMP_TOLERANCE_CONFIG = "EA_WEBHOOK_TIMESTAMP_TOLERANCE_SECONDS";
    private static final long DEFAULT_TOLERANCE_SECONDS = 300;
    private static final Map<String, Long> REPLAY_CACHE = new ConcurrentHashMap<>();

    private WebhookRequestVerifier() {
    }

    static Verification verify(HttpServletRequest request, String serviceType, String body) {
        String secretKey = serviceSecretKey(serviceType);
        String secret = firstNonBlank(
                Environment.getInstance().get(secretKey, ""),
                Environment.getInstance().get(GLOBAL_SECRET_CONFIG, ""));
        if (isBlank(secret)) {
            return Verification.rejected(401, "Webhook verification is not configured");
        }

        String timestamp = firstNonBlank(
                request.getHeader("X-EveryArrow-Webhook-Timestamp"),
                request.getHeader("X-Webhook-Timestamp"));
        if (isBlank(timestamp)) {
            return Verification.rejected(401, "Webhook timestamp is required");
        }

        Instant signedAt = parseTimestamp(timestamp);
        if (signedAt == null || !isWithinTolerance(signedAt)) {
            return Verification.rejected(401, "Webhook timestamp is outside the allowed window");
        }

        String signature = firstNonBlank(
                request.getHeader("X-EveryArrow-Webhook-Signature"),
                request.getHeader("X-Hub-Signature-256"));
        if (isBlank(signature)) {
            return Verification.rejected(401, "Webhook signature is required");
        }

        String normalizedSignature = normalizeSignature(signature);
        String expectedSignature = hmacSha256(secret.trim(), timestamp.trim() + "." + nullToEmpty(body));
        if (!constantTimeEquals(normalizedSignature, expectedSignature)) {
            return Verification.rejected(401, "Webhook signature is invalid");
        }

        String replayKey = serviceType + ":" + normalizedSignature;
        evictExpiredReplayEntries();
        Long previous = REPLAY_CACHE.putIfAbsent(replayKey, signedAt.toEpochMilli());
        if (previous != null) {
            return Verification.rejected(401, "Webhook signature has already been used");
        }

        return Verification.accepted(secretKey);
    }

    static void resetReplayCacheForTests() {
        REPLAY_CACHE.clear();
    }

    static String hmacSha256(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to calculate webhook signature", ex);
        }
    }

    private static boolean isWithinTolerance(Instant signedAt) {
        long toleranceSeconds = timestampToleranceSeconds();
        Instant now = Instant.now();
        return !signedAt.isBefore(now.minusSeconds(toleranceSeconds))
                && !signedAt.isAfter(now.plusSeconds(toleranceSeconds));
    }

    private static long timestampToleranceSeconds() {
        String configured = Environment.getInstance().get(TIMESTAMP_TOLERANCE_CONFIG, "");
        if (isBlank(configured)) {
            return DEFAULT_TOLERANCE_SECONDS;
        }
        try {
            return Math.max(1, Long.parseLong(configured.trim()));
        } catch (NumberFormatException ignored) {
            return DEFAULT_TOLERANCE_SECONDS;
        }
    }

    private static Instant parseTimestamp(String value) {
        if (isBlank(value)) {
            return null;
        }
        String trimmed = value.trim();
        try {
            long numeric = Long.parseLong(trimmed);
            if (trimmed.length() > 10) {
                return Instant.ofEpochMilli(numeric);
            }
            return Instant.ofEpochSecond(numeric);
        } catch (NumberFormatException ignored) {
            try {
                return Instant.parse(trimmed);
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }

    private static void evictExpiredReplayEntries() {
        long cutoff = Instant.now().minusSeconds(timestampToleranceSeconds()).toEpochMilli();
        REPLAY_CACHE.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    }

    private static String serviceSecretKey(String serviceType) {
        String normalized = isBlank(serviceType)
                ? "UNKNOWN"
                : serviceType.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_");
        return GLOBAL_SECRET_CONFIG + "_" + normalized;
    }

    private static String normalizeSignature(String signature) {
        String normalized = signature == null ? "" : signature.trim().toLowerCase();
        if (normalized.startsWith("sha256=")) {
            normalized = normalized.substring("sha256=".length());
        }
        return normalized;
    }

    private static boolean constantTimeEquals(String actual, String expected) {
        byte[] actualBytes = nullToEmpty(actual).getBytes(StandardCharsets.UTF_8);
        byte[] expectedBytes = nullToEmpty(expected).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(actualBytes, expectedBytes);
    }

    private static String firstNonBlank(String first, String second) {
        if (!isBlank(first)) {
            return first.trim();
        }
        if (!isBlank(second)) {
            return second.trim();
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    record Verification(boolean accepted, int status, String error, String secretSource) {
        static Verification accepted(String secretSource) {
            return new Verification(true, 200, null, secretSource);
        }

        static Verification rejected(int status, String error) {
            return new Verification(false, status, error, null);
        }
    }
}
