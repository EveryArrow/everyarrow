package com.everyarrow.eventengine.core.credits;

import com.everyarrow.utilities.environment.Environment;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

/**
 * Verifies compact signed local execution-license tokens.
 */
public class ExecutionLicenseTokenVerifier {
    public static final String PUBLIC_KEY_CONFIG = "EA_EXECUTION_LICENSE_PUBLIC_KEY";
    public static final String KEY_ID_CONFIG = "EA_EXECUTION_LICENSE_KEY_ID";

    private static final String ALGORITHM = "Ed25519";
    private static final Set<String> LOCAL_LICENSE_TYPES = Set.of("local_trial", "local_production");

    private final Environment environment;

    public ExecutionLicenseTokenVerifier() {
        this(Environment.getInstance());
    }

    ExecutionLicenseTokenVerifier(Environment environment) {
        this.environment = environment == null ? Environment.getInstance() : environment;
    }

    public VerifiedExecutionLicense verify(String token) {
        String trimmedToken = trimToNull(token);
        if (trimmedToken == null) {
            throw failure(ExecutionLicenseImportFailureReason.TOKEN_REQUIRED, "License token is required");
        }

        String[] segments = trimmedToken.split("\\.", -1);
        if (segments.length != 3 || isBlank(segments[0]) || isBlank(segments[1]) || isBlank(segments[2])) {
            throw failure(ExecutionLicenseImportFailureReason.INVALID_TOKEN,
                    "License token must have header, payload, and signature segments");
        }

        JsonObject header = parseJsonSegment(segments[0], "header");
        JsonObject payload = parseJsonSegment(segments[1], "payload");
        validateHeader(header);
        verifySignature(segments[0] + "." + segments[1], segments[2]);
        return verifiedPayload(payload, stringValue(header, "kid"));
    }

    private void validateHeader(JsonObject header) {
        String alg = stringValue(header, "alg");
        if (!ALGORITHM.equals(alg)) {
            throw failure(ExecutionLicenseImportFailureReason.UNSUPPORTED_ALGORITHM,
                    "Unsupported license token algorithm");
        }

        String configuredKeyId = trimToNull(environment.get(KEY_ID_CONFIG, ""));
        String tokenKeyId = stringValue(header, "kid");
        if (tokenKeyId == null) {
            throw failure(ExecutionLicenseImportFailureReason.INVALID_TOKEN,
                    "License token key id is required");
        }
        if (configuredKeyId != null && !configuredKeyId.equals(tokenKeyId)) {
            throw failure(ExecutionLicenseImportFailureReason.KEY_ID_MISMATCH,
                    "License token key id does not match the configured verifier");
        }
    }

    private void verifySignature(String signingInput, String signatureSegment) {
        try {
            String publicKeyValue = trimToNull(environment.get(PUBLIC_KEY_CONFIG, ""));
            if (publicKeyValue == null) {
                throw failure(ExecutionLicenseImportFailureReason.VERIFIER_UNAVAILABLE,
                        PUBLIC_KEY_CONFIG + " is required");
            }

            byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyValue);
            PublicKey publicKey = KeyFactory.getInstance(ALGORITHM)
                    .generatePublic(new X509EncodedKeySpec(publicKeyBytes));
            Signature verifier = Signature.getInstance(ALGORITHM);
            verifier.initVerify(publicKey);
            verifier.update(signingInput.getBytes(StandardCharsets.US_ASCII));

            if (!verifier.verify(base64UrlDecode(signatureSegment))) {
                throw failure(ExecutionLicenseImportFailureReason.INVALID_TOKEN,
                        "License token signature is invalid");
            }
        } catch (ExecutionLicenseImportException ex) {
            throw ex;
        } catch (Exception ex) {
            throw failure(ExecutionLicenseImportFailureReason.VERIFIER_UNAVAILABLE,
                    "License verifier could not validate the token");
        }
    }

    private VerifiedExecutionLicense verifiedPayload(JsonObject payload, String keyId) {
        String licenseId = requiredString(payload, "licenseId");
        requireUuid(licenseId, "licenseId");
        String licenseType = requiredString(payload, "licenseType");
        if (!LOCAL_LICENSE_TYPES.contains(licenseType)) {
            throw failure(ExecutionLicenseImportFailureReason.INVALID_TOKEN,
                    "License token is not a local execution license");
        }

        String customerId = requiredString(payload, "customerId");
        String installationId = requiredString(payload, "installationId");
        long totalCredits = requiredNonNegativeLong(payload, "totalCredits");
        String validFrom = requiredInstant(payload, "validFrom");
        String validUntil = optionalInstant(payload, "validUntil");
        String issuedAt = requiredInstant(payload, "issuedAt");

        JsonObject metadata = new JsonObject();
        if (payload.has("metadata") && payload.get("metadata").isJsonObject()) {
            metadata = payload.getAsJsonObject("metadata").deepCopy().getAsJsonObject();
        }

        return new VerifiedExecutionLicense(
                licenseId,
                licenseType,
                customerId,
                installationId,
                totalCredits,
                validFrom,
                validUntil,
                issuedAt,
                keyId,
                metadata);
    }

    private JsonObject parseJsonSegment(String segment, String label) {
        try {
            JsonElement parsed = JsonParser.parseString(new String(base64UrlDecode(segment), StandardCharsets.UTF_8));
            if (parsed == null || !parsed.isJsonObject()) {
                throw failure(ExecutionLicenseImportFailureReason.INVALID_TOKEN,
                        "License token " + label + " must be a JSON object");
            }
            return parsed.getAsJsonObject();
        } catch (ExecutionLicenseImportException ex) {
            throw ex;
        } catch (Exception ex) {
            throw failure(ExecutionLicenseImportFailureReason.INVALID_TOKEN,
                    "License token " + label + " is not valid JSON");
        }
    }

    private byte[] base64UrlDecode(String value) {
        try {
            return Base64.getUrlDecoder().decode(paddedBase64Url(value));
        } catch (Exception ex) {
            throw failure(ExecutionLicenseImportFailureReason.INVALID_TOKEN,
                    "License token contains invalid base64url data");
        }
    }

    private String paddedBase64Url(String value) {
        int remainder = value.length() % 4;
        if (remainder == 0) {
            return value;
        }
        return value + "=".repeat(4 - remainder);
    }

    private String requiredString(JsonObject object, String key) {
        String value = stringValue(object, key);
        if (value == null) {
            throw failure(ExecutionLicenseImportFailureReason.INVALID_TOKEN,
                    "License token payload is missing " + key);
        }
        return value;
    }

    private long requiredNonNegativeLong(JsonObject object, String key) {
        try {
            if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
                throw new IllegalArgumentException(key + " is missing");
            }
            long value = object.get(key).getAsLong();
            if (value < 0) {
                throw new IllegalArgumentException(key + " is negative");
            }
            return value;
        } catch (Exception ex) {
            throw failure(ExecutionLicenseImportFailureReason.INVALID_TOKEN,
                    "License token payload has invalid " + key);
        }
    }

    private String requiredInstant(JsonObject object, String key) {
        String value = requiredString(object, key);
        parseInstant(value, key);
        return value;
    }

    private String optionalInstant(JsonObject object, String key) {
        String value = stringValue(object, key);
        if (value == null) {
            return null;
        }
        parseInstant(value, key);
        return value;
    }

    private void parseInstant(String value, String key) {
        try {
            Instant.parse(value);
        } catch (Exception ex) {
            throw failure(ExecutionLicenseImportFailureReason.INVALID_TOKEN,
                    "License token payload has invalid " + key);
        }
    }

    private void requireUuid(String value, String key) {
        try {
            UUID.fromString(value);
        } catch (Exception ex) {
            throw failure(ExecutionLicenseImportFailureReason.INVALID_TOKEN,
                    "License token payload has invalid " + key);
        }
    }

    private String stringValue(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        try {
            return trimToNull(object.get(key).getAsString());
        } catch (Exception ex) {
            return null;
        }
    }

    private ExecutionLicenseImportException failure(ExecutionLicenseImportFailureReason reason, String message) {
        return new ExecutionLicenseImportException(reason, message);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
