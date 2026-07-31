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

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionLicenseActivationEndToEndTest {

    @AfterEach
    void resetEnvironment() {
        Environment.reset();
    }

    @Test
    void signedLicenseImportsAndFundsLocalExecutionThroughEventManager(@TempDir Path pluginRoot) throws Exception {
        UUID licenseId = UUID.randomUUID();
        String installationId = "contract-e2e-installation-" + licenseId;
        KeyPair keyPair = configureLicenseEnvironment(installationId, licenseId);

        InMemoryExecutionCreditLedger ledger = new InMemoryExecutionCreditLedger();
        ExecutionCreditDbGateway gateway = ledger.gateway();
        DbExecutionCreditGate gate = new DbExecutionCreditGate(new DefaultExecutionCreditPolicy(), gateway);
        Engine engine = new Engine(pluginRoot.toFile(), gate);
        registerCreditHandler(engine, gateway);
        registerOpenRestHandler(engine, "contract.rest.execute");

        String token = signedToken(keyPair, licenseId, installationId);
        ServiceCallResponse importResponse = engine.execute(licenseImportCall(token));
        JsonObject importBody = importResponse.getBodyAsObject();
        assertEquals(200, importResponse.getHttpStatus());
        assertTrue(importBody.get("imported").getAsBoolean(), importBody.toString());
        assertEquals(500, importBody.getAsJsonObject("credits").get("available").getAsLong());

        ServiceCallResponse statusResponse = engine.execute(statusCall());
        JsonObject statusBody = statusResponse.getBodyAsObject();
        assertEquals(200, statusResponse.getHttpStatus());
        assertEquals(licenseId.toString(), statusBody.getAsJsonObject("license").get("id").getAsString());
        assertEquals(500, statusBody.getAsJsonObject("credits").get("available").getAsLong());

        ServiceCallResponse executionResponse = engine.execute(restExecutionCall("contract.rest.execute"));
        assertEquals(200, executionResponse.getHttpStatus());
        assertTrue(executionResponse.getBodyAsObject().get("ok").getAsBoolean());

        ServiceCallResponse postExecutionStatus = engine.execute(statusCall());
        JsonObject postStatusBody = postExecutionStatus.getBodyAsObject();
        assertEquals(1, postStatusBody.getAsJsonObject("credits").get("consumed").getAsLong());
        assertEquals(499, postStatusBody.getAsJsonObject("credits").get("available").getAsLong());
    }

    private KeyPair configureLicenseEnvironment(String installationId, UUID licenseId) throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Environment.put(ExecutionCreditGateFactory.MODE_CONFIG, ExecutionCreditGateFactory.MODE_LOCAL);
        Environment.put(ExecutionLicenseTokenVerifier.PUBLIC_KEY_CONFIG,
                Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        Environment.put(ExecutionLicenseTokenVerifier.KEY_ID_CONFIG, "contract-e2e-key");
        Environment.put(ExecutionCreditContextResolver.INSTALLATION_ID_CONFIG, installationId);
        Environment.put(ExecutionCreditContextResolver.LICENSE_ID_CONFIG, licenseId.toString());
        return keyPair;
    }

    private String signedToken(KeyPair keyPair, UUID licenseId, String installationId) throws Exception {
        JsonObject header = new JsonObject();
        header.addProperty("alg", "Ed25519");
        header.addProperty("kid", "contract-e2e-key");
        JsonObject payload = new JsonObject();
        payload.addProperty("licenseId", licenseId.toString());
        payload.addProperty("licenseType", "local_trial");
        payload.addProperty("customerId", "contract-e2e-customer");
        payload.addProperty("installationId", installationId);
        payload.addProperty("totalCredits", 500);
        payload.addProperty("validFrom", "2026-06-15T00:00:00Z");
        payload.addProperty("validUntil", "2027-06-15T00:00:00Z");
        payload.addProperty("issuedAt", "2026-06-15T00:00:00Z");
        JsonObject metadata = new JsonObject();
        metadata.addProperty("seededBy", "ExecutionLicenseActivationEndToEndTest");
        payload.add("metadata", metadata);

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

    private void registerCreditHandler(Engine engine, ExecutionCreditDbGateway gateway) {
        Environment environment = Environment.getInstance();
        ExecutionCreditContextResolver contextResolver = new ExecutionCreditContextResolver(environment);
        ExecutionCreditStatusHandler handler = new ExecutionCreditStatusHandler(
                new ExecutionCreditStatusService(environment, gateway, contextResolver),
                new ExecutionLicenseImportService(
                        new ExecutionLicenseTokenVerifier(environment),
                        gateway,
                        contextResolver));
        for (ServiceCallDefinition definition
                : AnnotationDefinitionBuilder.buildDefinitions(ExecutionCreditStatusHandler.class)) {
            engine.getRegistry().register(definition.getServiceType(), handler, definition);
        }
    }

    private void registerOpenRestHandler(Engine engine, String serviceType) {
        ServiceCallDefinition definition = new ServiceCallDefinition();
        definition.setServiceType(serviceType);
        definition.setSource("core:contract");
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
}
