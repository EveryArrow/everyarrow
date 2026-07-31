package com.everyarrow.eventengine.core.credits;

import com.everyarrow.eventengine.core.services.runtime.ServiceCall;
import com.everyarrow.utilities.environment.Environment;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionLicenseImportServiceTest {

    @AfterEach
    void resetEnvironment() {
        Environment.reset();
    }

    @Test
    void validSignedLicenseImportsAndMapsDbResponse() throws Exception {
        KeyPair keyPair = configureVerifier("local-key-1");
        UUID licenseId = UUID.randomUUID();
        String token = signedToken(keyPair, "local-key-1", licensePayload(licenseId, "installation-1"));
        AtomicReference<JsonObject> capturedParams = new AtomicReference<>();
        ExecutionLicenseImportService service = new ExecutionLicenseImportService(
                new ExecutionLicenseTokenVerifier(Environment.getInstance()),
                new ExecutionCreditDbGateway((database, schema, function, jsonType, params) -> {
                    capturedParams.set(params);
                    JsonObject result = new JsonObject();
                    result.addProperty("imported", true);
                    result.addProperty("licenseId", licenseId.toString());
                    result.addProperty("licenseType", "local_trial");
                    result.addProperty("status", "active");
                    result.addProperty("customerId", "customer-1");
                    result.addProperty("installationId", "installation-1");
                    JsonObject credits = new JsonObject();
                    credits.addProperty("total", 500);
                    credits.addProperty("reserved", 0);
                    credits.addProperty("consumed", 0);
                    credits.addProperty("available", 500);
                    result.add("credits", credits);
                    JsonObject status = new JsonObject();
                    status.addProperty("mode", "local");
                    status.addProperty("ledgerAvailable", true);
                    status.addProperty("enforcementActive", true);
                    result.add("statusPayload", status);
                    return result;
                }),
                new ExecutionCreditContextResolver(Environment.getInstance()));

        JsonObject response = service.importLicense(serviceCall(token));

        assertTrue(response.get("imported").getAsBoolean());
        assertEquals(licenseId.toString(), response.get("licenseId").getAsString());
        assertEquals(500, response.getAsJsonObject("credits").get("available").getAsLong());
        assertTrue(response.getAsJsonObject("statusPayload").get("ledgerAvailable").getAsBoolean());
        assertEquals(licenseId.toString(), capturedParams.get().get("licenseId").getAsString());
        assertEquals("local_trial", capturedParams.get().get("licenseType").getAsString());
        assertEquals("customer-1", capturedParams.get().get("customerId").getAsString());
        assertEquals("installation-1", capturedParams.get().get("installationId").getAsString());
        assertEquals(500, capturedParams.get().get("totalCredits").getAsLong());
        assertEquals("local-key-1", capturedParams.get().get("keyId").getAsString());
        assertTrue(capturedParams.get().has("verifiedAt"));
    }

    @Test
    void invalidSignatureIsDeniedBeforeDbCall() throws Exception {
        configureVerifier("local-key-1");
        KeyPair differentKeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String token = signedToken(differentKeyPair, "local-key-1",
                licensePayload(UUID.randomUUID(), "installation-1"));
        AtomicInteger dbCalls = new AtomicInteger();
        ExecutionLicenseImportService service = serviceWithFailingGateway(dbCalls);

        JsonObject response = service.importLicense(serviceCall(token));

        assertFalse(response.get("imported").getAsBoolean());
        assertEquals(ExecutionLicenseImportFailureReason.INVALID_TOKEN.name(),
                response.get("reason").getAsString());
        assertEquals(0, dbCalls.get());
    }

    @Test
    void unsupportedAlgorithmIsDeniedBeforeDbCall() throws Exception {
        KeyPair keyPair = configureVerifier("local-key-1");
        JsonObject header = new JsonObject();
        header.addProperty("alg", "RS256");
        header.addProperty("kid", "local-key-1");
        String token = signedToken(keyPair, header, licensePayload(UUID.randomUUID(), "installation-1"));
        AtomicInteger dbCalls = new AtomicInteger();
        ExecutionLicenseImportService service = serviceWithFailingGateway(dbCalls);

        JsonObject response = service.importLicense(serviceCall(token));

        assertFalse(response.get("imported").getAsBoolean());
        assertEquals(ExecutionLicenseImportFailureReason.UNSUPPORTED_ALGORITHM.name(),
                response.get("reason").getAsString());
        assertEquals(0, dbCalls.get());
    }

    @Test
    void missingVerifierConfigFailsBeforeDbCall() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String token = signedToken(keyPair, "local-key-1",
                licensePayload(UUID.randomUUID(), "installation-1"));
        AtomicInteger dbCalls = new AtomicInteger();
        ExecutionLicenseImportService service = serviceWithFailingGateway(dbCalls);

        JsonObject response = service.importLicense(serviceCall(token));

        assertFalse(response.get("imported").getAsBoolean());
        assertEquals(ExecutionLicenseImportFailureReason.VERIFIER_UNAVAILABLE.name(),
                response.get("reason").getAsString());
        assertEquals(0, dbCalls.get());
    }

    @Test
    void installationMismatchDeniesBeforeDbCall() throws Exception {
        KeyPair keyPair = configureVerifier("local-key-1");
        Environment.put(ExecutionCreditContextResolver.INSTALLATION_ID_CONFIG, "installation-runtime");
        String token = signedToken(keyPair, "local-key-1",
                licensePayload(UUID.randomUUID(), "installation-token"));
        AtomicInteger dbCalls = new AtomicInteger();
        ExecutionLicenseImportService service = serviceWithFailingGateway(dbCalls);

        JsonObject response = service.importLicense(serviceCall(token));

        assertFalse(response.get("imported").getAsBoolean());
        assertEquals(ExecutionLicenseImportFailureReason.INSTALLATION_MISMATCH.name(),
                response.get("reason").getAsString());
        assertEquals(0, dbCalls.get());
    }

    @Test
    void dbImportFailureReturnsLedgerUnavailablePayload() throws Exception {
        KeyPair keyPair = configureVerifier("local-key-1");
        String token = signedToken(keyPair, "local-key-1",
                licensePayload(UUID.randomUUID(), "installation-1"));
        ExecutionLicenseImportService service = new ExecutionLicenseImportService(
                new ExecutionLicenseTokenVerifier(Environment.getInstance()),
                new ExecutionCreditDbGateway((database, schema, function, jsonType, params) -> {
                    throw new IllegalStateException("database unavailable");
                }),
                new ExecutionCreditContextResolver(Environment.getInstance()));

        JsonObject response = service.importLicense(serviceCall(token));

        assertFalse(response.get("imported").getAsBoolean());
        assertEquals(ExecutionLicenseImportFailureReason.LEDGER_UNAVAILABLE.name(),
                response.get("reason").getAsString());
        assertFalse(response.getAsJsonObject("statusPayload").get("ledgerAvailable").getAsBoolean());
    }

    private ExecutionLicenseImportService serviceWithFailingGateway(AtomicInteger dbCalls) {
        return new ExecutionLicenseImportService(
                new ExecutionLicenseTokenVerifier(Environment.getInstance()),
                new ExecutionCreditDbGateway((database, schema, function, jsonType, params) -> {
                    dbCalls.incrementAndGet();
                    throw new AssertionError("invalid import should not call finance");
                }),
                new ExecutionCreditContextResolver(Environment.getInstance()));
    }

    private KeyPair configureVerifier(String keyId) throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Environment.put(ExecutionLicenseTokenVerifier.PUBLIC_KEY_CONFIG,
                Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        Environment.put(ExecutionLicenseTokenVerifier.KEY_ID_CONFIG, keyId);
        return keyPair;
    }

    private ServiceCall serviceCall(String token) {
        ServiceCall call = new ServiceCall();
        call.setType(ExecutionCreditServiceContract.LICENSE_IMPORT);
        JsonObject params = new JsonObject();
        params.addProperty("licenseToken", token);
        call.setParams(params);
        return call;
    }

    private JsonObject licensePayload(UUID licenseId, String installationId) {
        JsonObject payload = new JsonObject();
        payload.addProperty("licenseId", licenseId.toString());
        payload.addProperty("licenseType", "local_trial");
        payload.addProperty("customerId", "customer-1");
        payload.addProperty("installationId", installationId);
        payload.addProperty("totalCredits", 500);
        payload.addProperty("validFrom", "2026-06-01T00:00:00Z");
        payload.addProperty("validUntil", "2027-06-01T00:00:00Z");
        payload.addProperty("issuedAt", Instant.parse("2026-06-15T00:00:00Z").toString());
        JsonObject metadata = new JsonObject();
        metadata.addProperty("plan", "trial");
        payload.add("metadata", metadata);
        return payload;
    }

    private String signedToken(KeyPair keyPair, String keyId, JsonObject payload) throws Exception {
        JsonObject header = new JsonObject();
        header.addProperty("alg", "Ed25519");
        header.addProperty("kid", keyId);
        return signedToken(keyPair, header, payload);
    }

    private String signedToken(KeyPair keyPair, JsonObject header, JsonObject payload) throws Exception {
        String headerSegment = base64Url(header.toString().getBytes(StandardCharsets.UTF_8));
        String payloadSegment = base64Url(payload.toString().getBytes(StandardCharsets.UTF_8));
        String signingInput = headerSegment + "." + payloadSegment;
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate());
        signer.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return signingInput + "." + base64Url(signer.sign());
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
