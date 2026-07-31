package com.everyarrow.eventengine.tests.core;

import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.eventengine.core.security.SecurityServiceContract;
import com.everyarrow.eventengine.core.routing.SecurityMetadataValidator;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.everyarrow.utilities.environment.Environment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityMetadataValidatorTest {

    @AfterEach
    void resetEnvironment() {
        Environment.reset();
    }

    @Test
    void warnsWhenProtectedExternalRouteHasNoResolvableTargetApplication() {
        Engine engine = new Engine();
        engine.getRegistry().registerDefinition(restDefinition("mystery.demo.protected", "/mystery/protected", false));

        String stderr = captureStderr(() -> SecurityMetadataValidator.validate(engine));

        assertTrue(stderr.contains("mystery.demo.protected"));
        assertTrue(stderr.contains("MYSTERY_CONSOLE_ID"));
    }

    @Test
    void doesNotWarnWhenRouteIsExplicitlyPublic() {
        Engine engine = new Engine();
        engine.getRegistry().registerDefinition(restDefinition("marketing.demo.public_demo", "/marketing/public", true));

        String stderr = captureStderr(() -> SecurityMetadataValidator.validate(engine));

        assertFalse(stderr.contains("marketing.demo.public_demo"));
    }

    @Test
    void doesNotWarnWhenConsoleIdEnvIsResolvable() {
        Environment.put("MYSTERY_CONSOLE_ID", "mystery-app-123");

        Engine engine = new Engine();
        engine.getRegistry().registerDefinition(restDefinition("mystery.demo.protected", "/mystery/protected", false));

        String stderr = captureStderr(() -> SecurityMetadataValidator.validate(engine));

        assertFalse(stderr.contains("mystery.demo.protected"));
    }

    @Test
    void doesNotWarnForHostedSecurityRouteWhenEventManagerConsoleIdIsResolvable() {
        Environment.put("EVENT_MANAGER_CONSOLE_ID", "event-manager-app-123");

        Engine engine = new Engine();
        engine.getRegistry().registerDefinition(securityRestDefinition(
                SecurityServiceContract.ROLE_TEMPLATE_SELECT_ALL,
                "/app-security/role-templates",
                false));

        String stderr = captureStderr(() -> SecurityMetadataValidator.validate(engine));

        assertFalse(stderr.contains(SecurityServiceContract.ROLE_TEMPLATE_SELECT_ALL));
    }

    @Test
    void hostedSecurityWarningNamesEventManagerAndSecurityFallbacks() {
        Engine engine = new Engine();
        engine.getRegistry().registerDefinition(securityRestDefinition(
                SecurityServiceContract.ROLE_TEMPLATE_SELECT_ALL,
                "/app-security/role-templates",
                false));

        String stderr = captureStderr(() -> SecurityMetadataValidator.validate(engine));

        assertTrue(stderr.contains("EVENT_MANAGER_CONSOLE_ID"));
        assertTrue(stderr.contains("SECURITY_CONSOLE_ID"));
        assertTrue(stderr.contains("CONSOLE_ID"));
    }

    @Test
    void doesNotWarnWhenRouteUsesEventManagerApplicationKey() {
        Environment.put("EVENT_MANAGER_CONSOLE_ID", "event-manager-app-123");

        Engine engine = new Engine();
        ServiceCallDefinition definition = restDefinition("engineShutdown", "/engine/shutdown", false);
        ServiceCallDefinition.CallMeta callMeta = new ServiceCallDefinition.CallMeta();
        callMeta.applicationKey = "EVENT_MANAGER";
        definition.getAnnotations().call = callMeta;
        engine.getRegistry().registerDefinition(definition);

        String stderr = captureStderr(() -> SecurityMetadataValidator.validate(engine));

        assertFalse(stderr.contains("engineShutdown"));
    }

    private static ServiceCallDefinition restDefinition(String serviceType, String path, boolean isPublic) {
        ServiceCallDefinition definition = new ServiceCallDefinition();
        definition.setServiceType(serviceType);
        definition.setSource("test");

        ServiceCallDefinition.Annotations annotations = new ServiceCallDefinition.Annotations();
        ServiceCallDefinition.RestRouteMeta route = new ServiceCallDefinition.RestRouteMeta();
        route.method = "POST";
        route.path = path;
        annotations.restRoute = route;

        if (isPublic) {
            ServiceCallDefinition.SecuredMeta secured = new ServiceCallDefinition.SecuredMeta();
            secured.isPublic = true;
            annotations.secured = secured;
        }

        definition.setAnnotations(annotations);
        return definition;
    }

    private static ServiceCallDefinition securityRestDefinition(String serviceType, String path, boolean isPublic) {
        ServiceCallDefinition definition = restDefinition(serviceType, path, isPublic);
        ServiceCallDefinition.CallMeta callMeta = new ServiceCallDefinition.CallMeta();
        callMeta.applicationKey = SecurityServiceContract.APPLICATION_KEY;
        definition.getAnnotations().call = callMeta;
        return definition;
    }

    private static String captureStderr(ThrowingRunnable action) {
        PrintStream original = System.err;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            System.setErr(capture);
            action.run();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            System.setErr(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
