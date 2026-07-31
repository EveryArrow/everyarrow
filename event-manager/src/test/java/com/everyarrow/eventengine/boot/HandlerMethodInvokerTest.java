package com.everyarrow.eventengine.boot;

import com.everyarrow.eventengine.core.services.registry.ServiceRegistry;
import com.everyarrow.eventengine.core.services.runtime.ServiceCall;
import com.everyarrow.eventengine.spi.handler.ServiceCallHandler;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HandlerMethodInvokerTest {

    @Test
    void invokesTargetMethodThatAcceptsOnlyParams() throws Exception {
        ServiceRegistry registry = new ServiceRegistry();
        ServiceCallDefinition definition = new ServiceCallDefinition();
        definition.setServiceType("site.ingress.validate_ddl");
        definition.setTargetMethodName("validateDdl");
        registry.registerDefinition(definition);

        ServiceCall call = new ServiceCall();
        call.setType(definition.getServiceType());
        JsonObject params = new JsonObject();
        params.addProperty("ddl_text", "CREATE TABLE customer (id BIGINT PRIMARY KEY)");
        call.setParams(params);

        JsonElement result = new HandlerMethodInvoker(registry)
                .invoke(new ParamsOnlyHandler(), definition.getServiceType(), call);

        assertEquals(params.get("ddl_text"), result.getAsJsonObject().get("ddl_text"));
    }

    private static final class ParamsOnlyHandler implements ServiceCallHandler {

        JsonObject validateDdl(JsonObject params) {
            return params;
        }

        @Override
        public JsonElement execute(JsonObject params, String type, String source) {
            throw new AssertionError("Target method should be invoked");
        }
    }
}
