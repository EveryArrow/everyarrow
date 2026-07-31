package com.everyarrow.eventengine.boot;

import com.everyarrow.eventengine.core.services.registry.ServiceRegistry;
import com.everyarrow.eventengine.core.services.runtime.ServiceCall;
import com.everyarrow.eventengine.spi.handler.ServiceCallHandler;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class HandlerMethodInvoker {

    private final ServiceRegistry registry;

    HandlerMethodInvoker(ServiceRegistry registry) {
        this.registry = registry;
    }

    JsonElement invoke(ServiceCallHandler handler, String serviceType, ServiceCall call) throws Exception {
        ServiceCallDefinition definition = registry.getDefinition(serviceType);

        if (definition != null && definition.hasTargetMethod()) {
            return invokeTargetMethod(handler, definition, serviceType, call);
        }

        return handler.execute(call);
    }

    private JsonElement invokeTargetMethod(ServiceCallHandler handler,
                                           ServiceCallDefinition definition,
                                           String serviceType,
                                           ServiceCall call) throws Exception {
        try {
            Method method = handler.getClass().getDeclaredMethod(
                    definition.getTargetMethodName(), ServiceCall.class);
            method.setAccessible(true);
            return (JsonElement) method.invoke(handler, call);
        } catch (InvocationTargetException ex) {
            throw causeAsException(ex);
        } catch (NoSuchMethodException missingPrimary) {
            return invokeParamsTargetMethod(handler, definition, serviceType, call, missingPrimary);
        }
    }

    private JsonElement invokeParamsTargetMethod(ServiceCallHandler handler,
                                                 ServiceCallDefinition definition,
                                                 String serviceType,
                                                 ServiceCall call,
                                                 NoSuchMethodException missingPrimary) throws Exception {
        try {
            Method paramsMethod = handler.getClass().getDeclaredMethod(
                    definition.getTargetMethodName(), JsonObject.class);
            paramsMethod.setAccessible(true);
            return (JsonElement) paramsMethod.invoke(handler, call.getParams());
        } catch (InvocationTargetException ex) {
            throw causeAsException(ex);
        } catch (NoSuchMethodException missingParams) {
            return invokeLegacyTargetMethod(
                    handler, definition, serviceType, call, missingPrimary, missingParams);
        }
    }

    private JsonElement invokeLegacyTargetMethod(ServiceCallHandler handler,
                                                 ServiceCallDefinition definition,
                                                 String serviceType,
                                                 ServiceCall call,
                                                 NoSuchMethodException missingPrimary,
                                                 NoSuchMethodException missingParams) throws Exception {
        try {
            Method legacyMethod = handler.getClass().getDeclaredMethod(
                    definition.getTargetMethodName(), JsonObject.class, String.class, String.class);
            legacyMethod.setAccessible(true);
            return (JsonElement) legacyMethod.invoke(handler, call.getParams(), serviceType, sourceName(call));
        } catch (InvocationTargetException ex) {
            throw causeAsException(ex);
        } catch (NoSuchMethodException missingLegacy) {
            IllegalStateException error = missingTargetMethod(handler, definition, serviceType);
            error.addSuppressed(missingPrimary);
            error.addSuppressed(missingParams);
            error.addSuppressed(missingLegacy);
            throw error;
        }
    }

    private IllegalStateException missingTargetMethod(ServiceCallHandler handler,
                                                      ServiceCallDefinition definition,
                                                      String serviceType) {
        return new IllegalStateException("Configured targetMethod '" + definition.getTargetMethodName()
                + "' for serviceType '" + serviceType
                + "' was not found on handler " + handler.getClass().getName()
                + " with a supported signature");
    }

    private Exception causeAsException(InvocationTargetException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof Exception exception) {
            return exception;
        }
        return new RuntimeException(cause);
    }

    private String sourceName(ServiceCall call) {
        return call == null || call.getSource() == null ? null : call.getSource().name();
    }
}
