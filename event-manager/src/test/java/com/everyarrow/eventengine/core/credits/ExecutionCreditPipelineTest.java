package com.everyarrow.eventengine.core.credits;

import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.eventengine.core.services.runtime.ServiceCall;
import com.everyarrow.eventengine.core.services.runtime.ServiceCallSource;
import com.everyarrow.eventengine.spi.handler.ServiceCallHandler;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.everyarrow.eventengine.spi.model.ServiceCallResponse;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionCreditPipelineTest {

    @Test
    void deniedGatePreventsHandlerInvocationAndReturnsPaymentRequired(@TempDir Path pluginRoot) throws Exception {
        AtomicBoolean handlerInvoked = new AtomicBoolean(false);
        Engine engine = new Engine(pluginRoot.toFile(), new DenyingGate());
        register(engine, "tests.execution.denied", (params, type, source) -> {
            handlerInvoked.set(true);
            return ok();
        });

        ServiceCall call = serviceCall("tests.execution.denied");
        ServiceCallResponse response = engine.execute(call);
        JsonObject body = response.getBodyAsObject();

        assertEquals(402, response.getHttpStatus());
        assertEquals("Execution credit denied", body.get("error").getAsString());
        assertEquals(CreditDenialReason.CREDITS_EXHAUSTED.name(), body.get("reason").getAsString());
        assertEquals(1, body.get("creditsRequired").getAsLong());
        assertEquals(0, body.get("creditsAvailable").getAsLong());
        assertTrue(body.has("correlationId"));
        assertFalse(handlerInvoked.get());
    }

    @Test
    void localDbGateDenialPreventsHandlerInvocationAndReturnsPaymentRequired(@TempDir Path pluginRoot) throws Exception {
        AtomicBoolean handlerInvoked = new AtomicBoolean(false);
        ExecutionCreditDbGateway gateway = new ExecutionCreditDbGateway((database, schema, function, jsonType, params) -> {
            JsonObject result = new JsonObject();
            result.addProperty("allowed", false);
            result.addProperty("reason", CreditDenialReason.LICENSE_REQUIRED.name());
            result.addProperty("creditsRequired", 1);
            result.addProperty("creditsAvailable", 0);
            result.addProperty("message", "License required");
            return result;
        });
        Engine engine = new Engine(pluginRoot.toFile(),
                new DbExecutionCreditGate(new DefaultExecutionCreditPolicy(), gateway));
        register(engine, "tests.execution.local-denied", (params, type, source) -> {
            handlerInvoked.set(true);
            return ok();
        });

        ServiceCallResponse response = engine.execute(serviceCall("tests.execution.local-denied"));
        JsonObject body = response.getBodyAsObject();

        assertEquals(402, response.getHttpStatus());
        assertEquals(CreditDenialReason.LICENSE_REQUIRED.name(), body.get("reason").getAsString());
        assertFalse(handlerInvoked.get());
    }

    @Test
    void pipelineSuccessFinalizesSucceeded(@TempDir Path pluginRoot) throws Exception {
        CapturingGate gate = new CapturingGate();
        Engine engine = new Engine(pluginRoot.toFile(), gate);
        register(engine, "tests.execution.success", (params, type, source) -> ok());

        ServiceCallResponse response = engine.execute(serviceCall("tests.execution.success"));

        assertEquals(200, response.getHttpStatus());
        assertEquals(1, gate.finalizations.size());
        assertEquals(ExecutionOutcome.SUCCEEDED, gate.finalizations.get(0).outcome());
    }

    @Test
    void pipelineHandlerFailureFinalizesFailed(@TempDir Path pluginRoot) throws Exception {
        CapturingGate gate = new CapturingGate();
        Engine engine = new Engine(pluginRoot.toFile(), gate);
        register(engine, "tests.execution.failure", (params, type, source) -> {
            throw new IllegalStateException("handler failed");
        });

        ServiceCallResponse response = engine.execute(serviceCall("tests.execution.failure"));

        assertEquals(500, response.getHttpStatus());
        assertEquals(1, gate.finalizations.size());
        ExecutionUsageFinalization finalization = gate.finalizations.get(0);
        assertEquals(ExecutionOutcome.FAILED, finalization.outcome());
        assertEquals("handler failed", finalization.message());
    }

    private ServiceCall serviceCall(String serviceType) {
        ServiceCall call = new ServiceCall();
        call.setType(serviceType);
        call.setSource(ServiceCallSource.INTERNAL);
        call.setParams(new JsonObject());
        return call;
    }

    private void register(Engine engine, String serviceType, ServiceCallHandler handler) {
        ServiceCallDefinition definition = new ServiceCallDefinition();
        definition.setServiceType(serviceType);
        definition.setSource("core:test");
        engine.getRegistry().register(serviceType, handler, definition);
    }

    private JsonObject ok() {
        JsonObject result = new JsonObject();
        result.addProperty("ok", true);
        return result;
    }

    private static final class DenyingGate implements ExecutionCreditGate {
        @Override
        public CreditDecision reserve(ExecutionCreditRequest request) {
            return CreditDecision.denied(
                    CreditDenialReason.CREDITS_EXHAUSTED,
                    1,
                    0,
                    "Upgrade required");
        }

        @Override
        public ExecutionUsageEvent finalizeUsage(CreditReservation reservation,
                                                 ExecutionUsageFinalization finalization) {
            throw new AssertionError("Denied executions should not finalize usage");
        }
    }

    private static final class CapturingGate implements ExecutionCreditGate {
        private final DefaultExecutionCreditPolicy policy = new DefaultExecutionCreditPolicy();
        private final List<ExecutionUsageFinalization> finalizations = new ArrayList<>();

        @Override
        public CreditDecision reserve(ExecutionCreditRequest request) {
            return CreditDecision.allowed(CreditReservation.create(request, policy.reservedCreditsFor(request)));
        }

        @Override
        public ExecutionUsageEvent finalizeUsage(CreditReservation reservation,
                                                 ExecutionUsageFinalization finalization) {
            finalizations.add(finalization);
            return ExecutionUsageEvent.create(reservation, finalization);
        }
    }
}
