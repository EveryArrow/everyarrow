package com.everyarrow.eventengine.boot;

import com.everyarrow.eventengine.core.credits.ExecutionCreditDeniedException;
import com.everyarrow.eventengine.core.hooks.HookManager;
import com.everyarrow.eventengine.core.hooks.SecurityAccessDeniedException;
import com.everyarrow.eventengine.core.hooks.SecurityAuthenticationException;
import com.everyarrow.eventengine.core.services.plugins.PluginManager;
import com.everyarrow.eventengine.core.services.registry.ServiceRegistry;
import com.everyarrow.eventengine.core.services.runtime.GenericProcessHandler;
import com.everyarrow.eventengine.core.services.runtime.ServiceCall;
import com.everyarrow.eventengine.spi.handler.ServiceCallHandler;
import com.everyarrow.eventengine.spi.model.ServiceCallResponse;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.lang.reflect.Constructor;
import java.util.UUID;
import java.util.logging.Logger;

final class ServiceCallPipeline {

    private static final Logger logger = Logger.getLogger(ServiceCallPipeline.class.getName());

    private final Engine engine;
    private final ServiceRegistry registry;
    private final HookManager hookManager;
    private final PluginManager pluginManager;
    private final HandlerMethodInvoker handlerInvoker;

    ServiceCallPipeline(Engine engine,
                        ServiceRegistry registry,
                        HookManager hookManager,
                        PluginManager pluginManager) {
        this.engine = engine;
        this.registry = registry;
        this.hookManager = hookManager;
        this.pluginManager = pluginManager;
        this.handlerInvoker = new HandlerMethodInvoker(registry);
    }

    ServiceCallResponse execute(ServiceCall call) throws Exception {
        requireServiceCall(call);
        call.setEngine(engine);

        long startedAt = System.currentTimeMillis();
        String serviceType = serviceType(call);
        ServiceCallHandler handler = handlerFor(serviceType);

        if (handler == null) {
            return notFound(serviceType, startedAt);
        }

        call.setHandler(handler);
        return executeWithHooks(call, serviceType, handler, startedAt);
    }

    private ServiceCallResponse executeWithHooks(ServiceCall call,
                                                 String serviceType,
                                                 ServiceCallHandler handler,
                                                 long startedAt) throws Exception {
        try {
            hookManager.before(call);
            JsonElement resultBody = handlerInvoker.invoke(handler, serviceType, call);
            hookManager.after(call, resultBody);
            return ServiceCallResponse.ok(serviceType, resultBody, durationSince(startedAt))
                    .withHttpStatus(resultHttpStatus(resultBody));
        } catch (Exception ex) {
            hookManager.error(call, ex);
            return ServiceCallResponse.ok(serviceType, sanitizedErrorBody(ex), durationSince(startedAt))
                    .withHttpStatus(resolveHttpStatus(ex));
        }
    }

    private int resultHttpStatus(JsonElement resultBody) {
        if (resultBody == null || !resultBody.isJsonObject()) {
            return 200;
        }
        JsonObject body = resultBody.getAsJsonObject();
        if (body.has("success") && body.get("success").isJsonPrimitive()
                && body.get("success").getAsJsonPrimitive().isBoolean()
                && !body.get("success").getAsBoolean()) {
            return 500;
        }
        if (body.has("status") && body.get("status").isJsonPrimitive()
                && "error".equalsIgnoreCase(body.get("status").getAsString())) {
            return 500;
        }
        return 200;
    }

    private ServiceCallHandler handlerFor(String serviceType) {
        ServiceCallHandler handler = registry.get(serviceType);
        if (handler instanceof GenericProcessHandler && hasIndexedConcreteHandler(serviceType)) {
            registerLazyHandler(serviceType);
            ServiceCallHandler preferred = registry.get(serviceType);
            if (preferred != null) {
                return preferred;
            }
        }
        if (handler != null) {
            return handler;
        }

        registerLazyHandler(serviceType);
        return registry.get(serviceType);
    }

    private boolean hasIndexedConcreteHandler(String serviceType) {
        return pluginManager.getHandlerClassNameForService(serviceType)
                .filter(className -> !isGenericProcessHandlerName(className))
                .isPresent();
    }

    private boolean isGenericProcessHandlerName(String className) {
        return GenericProcessHandler.class.getName().equals(className)
                || GenericProcessHandler.class.getSimpleName().equals(className);
    }

    private void registerLazyHandler(String serviceType) {
        try {
            var definition = pluginManager.getDefinition(serviceType);
            var handlerClassName = pluginManager.getHandlerClassNameForService(serviceType);

            if (definition.isEmpty() || handlerClassName.isEmpty()) {
                return;
            }

            Class<?> handlerClass = resolveHandlerClass(handlerClassName.get());
            if (!ServiceCallHandler.class.isAssignableFrom(handlerClass)) {
                return;
            }

            ServiceCallHandler handler = instantiateHandler(handlerClass.asSubclass(ServiceCallHandler.class));
            registry.register(serviceType, handler, definition.get());
        } catch (Throwable ex) {
            logger.warning(() -> "Failed to lazily register handler for " + serviceType
                    + ": " + safeMessage(ex));
        }
    }

    private Class<?> resolveHandlerClass(String className) {
        return pluginManager.findClass(className)
                .orElseGet(() -> loadHostClass(className));
    }

    private Class<?> loadHostClass(String className) {
        try {
            return getClass().getClassLoader().loadClass(className);
        } catch (ClassNotFoundException ex) {
            throw new RuntimeException(ex);
        }
    }

    private ServiceCallHandler instantiateHandler(Class<? extends ServiceCallHandler> handlerClass) throws Exception {
        for (Constructor<?> constructor : handlerClass.getConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            if (parameters.length == 1 && parameters[0].equals(Engine.class)) {
                return (ServiceCallHandler) constructor.newInstance(engine);
            }
        }

        Constructor<? extends ServiceCallHandler> noArg = handlerClass.getDeclaredConstructor();
        noArg.setAccessible(true);
        return noArg.newInstance();
    }

    private ServiceCallResponse notFound(String serviceType, long startedAt) {
        JsonObject body = new JsonObject();
        body.addProperty("error", "Service not found");
        return ServiceCallResponse.ok(serviceType, body, durationSince(startedAt)).withHttpStatus(404);
    }

    private JsonObject sanitizedErrorBody(Exception ex) {
        String correlationId = UUID.randomUUID().toString();
        logger.warning(() -> "ServiceCall failed correlationId=" + correlationId
                + " type=" + (ex == null ? "unknown" : ex.getClass().getSimpleName())
                + " message=" + redact(ex == null ? null : ex.getMessage()));

        JsonObject body = new JsonObject();
        body.addProperty("error", clientErrorMessage(ex));
        if (ex instanceof ExecutionCreditDeniedException creditDenied) {
            body.addProperty("reason", creditDenied.getReason().name());
            body.addProperty("creditsRequired", creditDenied.getCreditsRequired());
            body.addProperty("creditsAvailable", creditDenied.getCreditsAvailable());
        }
        body.addProperty("correlationId", correlationId);
        return body;
    }

    private String clientErrorMessage(Exception ex) {
        if (ex instanceof SecurityAuthenticationException) {
            return "Authentication failed";
        }
        if (ex instanceof SecurityAccessDeniedException) {
            return "Access denied";
        }
        if (ex instanceof ExecutionCreditDeniedException) {
            return "Execution credit denied";
        }
        return "Internal server error";
    }

    private int resolveHttpStatus(Exception ex) {
        if (ex instanceof SecurityAuthenticationException) {
            return 401;
        }
        if (ex instanceof SecurityAccessDeniedException) {
            return 403;
        }
        if (ex instanceof ExecutionCreditDeniedException) {
            return 402;
        }
        return 500;
    }

    private String redact(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        return message
                .replaceAll("(?i)(api[_-]?key|secret|token|password)=\\S+", "$1=[REDACTED]")
                .replaceAll("(?i)(Bearer|ApiKey|Api-Key)\\s+\\S+", "$1 [REDACTED]");
    }

    private String safeMessage(Throwable ex) {
        return redact(ex == null ? null : ex.getMessage());
    }

    private long durationSince(long startedAt) {
        return System.currentTimeMillis() - startedAt;
    }

    private String serviceType(ServiceCall call) {
        String serviceType = call.getType();
        if (serviceType == null || serviceType.isEmpty()) {
            return "unknown";
        }
        return serviceType;
    }

    private void requireServiceCall(ServiceCall call) {
        if (call == null) {
            throw new IllegalArgumentException("ServiceCall must not be null");
        }
    }
}
