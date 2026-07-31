package com.everyarrow.eventengine.core.credits;

import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.eventengine.core.adapters.AnnotationDefinitionBuilder;
import com.everyarrow.eventengine.core.services.credits.ExecutionCreditStatusHandler;
import com.everyarrow.eventengine.core.services.runtime.ServiceCall;
import com.everyarrow.eventengine.core.services.runtime.ServiceCallSource;
import com.everyarrow.eventengine.spi.handler.ServiceCallHandler;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.everyarrow.eventengine.spi.model.ServiceCallResponse;
import com.everyarrow.utilities.environment.Environment;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Explicit PostgreSQL integration test for signed local license activation through Engine.execute().
 */
class ExecutionLicenseActivationPostgresIT {

    @AfterEach
    void resetEnvironment() {
        Environment.reset();
    }

    @Test
    void signedLicenseImportsAndFundsLocalExecutionThroughEventManager(@TempDir Path pluginRoot) throws Exception {
        String jdbcUrl = requireProperty("credit.ledger.jdbcUrl");
        String dbUser = requireProperty("credit.ledger.dbUser");
        String dbPassword = requireProperty("credit.ledger.dbPassword");
        boolean dbSsl = Boolean.parseBoolean(System.getProperty("credit.ledger.dbSsl", "false"));

        UUID licenseId = UUID.randomUUID();
        String installationId = "smoke-e2e-installation-" + licenseId;
        configureDbEnvironment(jdbcUrl, dbUser, dbPassword, dbSsl);
        configureLicenseEnvironment(installationId, licenseId);

        try {
            Engine engine = new Engine(pluginRoot.toFile());
            registerCreditHandler(engine);
            registerOpenRestHandler(engine, "smoke.rest.execute");

            String token = signedToken(licenseId, installationId);
            ServiceCallResponse importResponse = engine.execute(licenseImportCall(token));
            JsonObject importBody = importResponse.getBodyAsObject();
            assertEquals(200, importResponse.getHttpStatus());
            assertTrue(importBody.get("imported").getAsBoolean(), importBody.toString());
            assertEquals(500, importBody.getAsJsonObject("credits").get("available").getAsLong());

            ServiceCallResponse statusResponse = engine.execute(statusCall());
            JsonObject statusBody = statusResponse.getBodyAsObject();
            assertEquals(200, statusResponse.getHttpStatus());
            assertEquals(licenseId.toString(),
                    statusBody.getAsJsonObject("license").get("id").getAsString());
            assertEquals(500, statusBody.getAsJsonObject("credits").get("available").getAsLong());

            ServiceCallResponse executionResponse = engine.execute(restExecutionCall("smoke.rest.execute"));
            assertEquals(200, executionResponse.getHttpStatus());
            assertTrue(executionResponse.getBodyAsObject().get("ok").getAsBoolean());

            ServiceCallResponse postExecutionStatus = engine.execute(statusCall());
            JsonObject postStatusBody = postExecutionStatus.getBodyAsObject();
            assertEquals(1, postStatusBody.getAsJsonObject("credits").get("consumed").getAsLong());
            assertEquals(499, postStatusBody.getAsJsonObject("credits").get("available").getAsLong());
        } finally {
            cleanupLicenseRows(jdbcUrl, dbUser, dbPassword, licenseId);
        }
    }

    private void configureDbEnvironment(String jdbcUrl, String dbUser, String dbPassword, boolean dbSsl) throws Exception {
        URI uri = URI.create(jdbcUrl.substring("jdbc:postgresql:".length()));
        String dbName = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "");
        Environment.put(ExecutionCreditGateFactory.MODE_CONFIG, ExecutionCreditGateFactory.MODE_LOCAL);
        Environment.put("DB_HOST", uri.getHost());
        Environment.put("DB_PORT", String.valueOf(uri.getPort() > 0 ? uri.getPort() : 5432));
        Environment.put("DB_NAME", dbName);
        Environment.put("DB_USERNAME", dbUser);
        Environment.put("DB_PASSWORD", dbPassword);
        Environment.put("DB_SSL", Boolean.toString(dbSsl));
        Environment.put("EA_ALLOW_PLAINTEXT_DB", "true");
    }

    private void configureLicenseEnvironment(String installationId, UUID licenseId) throws Exception {
        KeyPair keyPair = keyPair();
        Environment.put(ExecutionLicenseTokenVerifier.PUBLIC_KEY_CONFIG,
                Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        Environment.put(ExecutionLicenseTokenVerifier.KEY_ID_CONFIG, "smoke-e2e-key");
        Environment.put(ExecutionCreditContextResolver.INSTALLATION_ID_CONFIG, installationId);
        Environment.put(ExecutionCreditContextResolver.LICENSE_ID_CONFIG, licenseId.toString());
        Environment.put("smoke.privateKey",
                Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
    }

    private String signedToken(UUID licenseId, String installationId) throws Exception {
        KeyPair keyPair = keyPairFromEnvironment();
        JsonObject header = new JsonObject();
        header.addProperty("alg", "Ed25519");
        header.addProperty("kid", "smoke-e2e-key");
        JsonObject payload = new JsonObject();
        payload.addProperty("licenseId", licenseId.toString());
        payload.addProperty("licenseType", "local_trial");
        payload.addProperty("customerId", "smoke-e2e-customer");
        payload.addProperty("installationId", installationId);
        payload.addProperty("totalCredits", 500);
        payload.addProperty("validFrom", "2026-06-15T00:00:00Z");
        payload.addProperty("validUntil", "2027-06-15T00:00:00Z");
        payload.addProperty("issuedAt", Instant.parse("2026-06-15T00:00:00Z").toString());
        JsonObject metadata = new JsonObject();
        metadata.addProperty("seededBy", "ExecutionLicenseActivationPostgresIT");
        payload.add("metadata", metadata);
        return signedToken(keyPair, header, payload);
    }

    private KeyPair keyPairFromEnvironment() throws Exception {
        byte[] privateKey = Base64.getDecoder().decode(Environment.getInstance().get("smoke.privateKey", ""));
        byte[] publicKey = Base64.getDecoder().decode(
                Environment.getInstance().get(ExecutionLicenseTokenVerifier.PUBLIC_KEY_CONFIG, ""));
        var keyFactory = java.security.KeyFactory.getInstance("Ed25519");
        return new KeyPair(
                keyFactory.generatePublic(new java.security.spec.X509EncodedKeySpec(publicKey)),
                keyFactory.generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(privateKey)));
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

    private KeyPair keyPair() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    private void registerCreditHandler(Engine engine) {
        ExecutionCreditStatusHandler handler = new ExecutionCreditStatusHandler();
        for (ServiceCallDefinition definition : AnnotationDefinitionBuilder.buildDefinitions(ExecutionCreditStatusHandler.class)) {
            engine.getRegistry().register(definition.getServiceType(), handler, definition);
        }
    }

    private void registerOpenRestHandler(Engine engine, String serviceType) {
        ServiceCallDefinition definition = new ServiceCallDefinition();
        definition.setServiceType(serviceType);
        definition.setSource("core:smoke");
        definition.annotations = new ServiceCallDefinition.Annotations();
        definition.annotations.call = new ServiceCallDefinition.CallMeta();
        definition.annotations.call.open = true;
        engine.getRegistry().register(serviceType, (ServiceCallHandler) (params, type, source) -> {
            JsonObject result = new JsonObject();
            result.addProperty("ok", true);
            return result;
        }, definition);
    }

    private ServiceCall licenseImportCall(String token) {
        ServiceCall call = new ServiceCall();
        call.setType(ExecutionCreditServiceContract.LICENSE_IMPORT);
        call.setSource(ServiceCallSource.INTERNAL);
        JsonObject params = new JsonObject();
        params.addProperty("licenseToken", token);
        call.setParams(params);
        return call;
    }

    private ServiceCall statusCall() {
        ServiceCall call = new ServiceCall();
        call.setType(ExecutionCreditServiceContract.STATUS);
        call.setSource(ServiceCallSource.INTERNAL);
        call.setParams(new JsonObject());
        return call;
    }

    private ServiceCall restExecutionCall(String serviceType) {
        ServiceCall call = new ServiceCall();
        call.setType(serviceType);
        call.setSource(ServiceCallSource.REST);
        call.setParams(new JsonObject());
        return call;
    }

    private String requireProperty(String key) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required system property: " + key);
        }
        return value;
    }

    private void cleanupLicenseRows(String jdbcUrl, String dbUser, String dbPassword, UUID licenseId) throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword)) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement("""
                        delete from finance.execution_usage_event
                        where license_id = ?::uuid
                        """)) {
                    statement.setString(1, licenseId.toString());
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        delete from finance.execution_credit_reservation
                        where license_id = ?::uuid
                        """)) {
                    statement.setString(1, licenseId.toString());
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        delete from finance.execution_license
                        where id = ?::uuid
                        """)) {
                    statement.setString(1, licenseId.toString());
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            }
        }
    }
}
