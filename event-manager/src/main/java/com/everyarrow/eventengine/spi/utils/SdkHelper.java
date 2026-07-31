package com.everyarrow.eventengine.spi.utils;

import com.everyarrow.eventengine.core.services.runtime.ServiceCall;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.Map;

/**
 * A reflection‐based dispatcher that:
 * 1) At startup, verifies that for each enum constant E:
 * • There is a Request class `{PascalCase(E)}Request` in the given package
 * • The clientClass has a method `camelCase(E)(Request)`
 * 2) At runtime:
 * • Injects `customer_id` from `auth_provider_id`
 * • Reflects out `QUERY_TYPE` or `EVENT_TYPE` from the request class and adds it as
 * `"queryType"` or `"eventType"` into the JSON
 * • Deserializes the JSON into the proper Request class via Gson
 * • Invokes the SDK client method
 *
 * @param <E> your enum type, e.g. MonitoringServiceCallType or SecurityServiceCallType
 */
public class SdkHelper<E extends Enum<E>> {
    private final Class<E> enumType;
    private final Object clientInstance;
    private final Class<?> clientClass;
    private final String requestPackage;
    private final Gson gson = new Gson();

    private final Map<E, Class<?>> requestClasses;
    private final Map<E, Method> clientMethods;

    /**
     * @param enumType        enum class (e.g. MonitoringServiceCallType.class)
     * @param clientInstance  SDK client instance (e.g. new PerchClient.Builder().build())
     * @param clientClassName fully‐qualified SDK client class name
     *                        (e.g. "com.everyarrow.utilities.sdk.perch.PerchClient")
     * @param requestPackage  package prefix for request models,
     *                        e.g. "com.everyarrow.utilities.sdk.perch.model.request"
     */
    public SdkHelper(Class<E> enumType,
                     Object clientInstance,
                     String clientClassName,
                     String requestPackage) {
        try {
            this.enumType = enumType;
            this.clientInstance = clientInstance;
            this.clientClass = Class.forName(clientClassName);
            this.requestPackage = requestPackage;
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("SDK client class not found: " + clientClassName, e);
        }

        this.requestClasses = new EnumMap<>(enumType);
        this.clientMethods = new EnumMap<>(enumType);
        initializeMappings();
    }

    private static String toCamelCase(String s) {
        StringBuilder sb = new StringBuilder();
        boolean up = false;
        for (char c : s.toCharArray()) {
            if (c == '_') {
                up = true;
                continue;
            }
            sb.append(up ? Character.toUpperCase(c) : c);
            up = false;
        }
        return sb.toString();
    }

    private static String toPascalCase(String s) {
        String c = toCamelCase(s);
        return Character.toUpperCase(c.charAt(0)) + c.substring(1);
    }

    private void initializeMappings() {
        for (E constant : enumType.getEnumConstants()) {
            String name = constant.name();
            String pascal = toPascalCase(name);
            String reqClsName = requestPackage + "." + pascal + "Request";
            String methodName = toCamelCase(name);

            try {
                Class<?> reqCls = Class.forName(reqClsName);
                Method sdkM = clientClass.getMethod(methodName, reqCls);

                requestClasses.put(constant, reqCls);
                clientMethods.put(constant, sdkM);

            } catch (ClassNotFoundException cnf) {
                throw new IllegalStateException(
                        "Missing request class for " + constant + ": " + reqClsName, cnf);
            } catch (NoSuchMethodException nsme) {
                throw new IllegalStateException(
                        "Missing SDK method for " + constant + ": " +
                                methodName + "(" + pascal + "Request)", nsme);
            }
        }
    }

    // ---- naming conventions ----

    /**
     * 1) adds "customer_id"
     * 2) reads static QUERY_TYPE / EVENT_TYPE from the Request class and adds
     * it as "queryType" or "eventType"
     * 3) Gson‐deserializes into the Request
     * 4) invokes the SDK method
     */
    public Object process(ServiceCall call) {
        JsonObject params = call.getParams();

        // 2) inject queryType or eventType
        E constant = Enum.valueOf(enumType, call.getType());
        Class<?> reqCls = requestClasses.get(constant);

        try {
            // look for a static TYPE field
            if (!tryInjectStatic(params, reqCls, "TYPE", "type")) {
                params.addProperty("type", constant.name());
            }

            // 3) Gson‐deserialize
            Object request = gson.fromJson(params, reqCls);

            // 4) invoke SDK
            Method sdkM = clientMethods.get(constant);
            return sdkM.invoke(clientInstance, request);

        } catch (InvocationTargetException ite) {
            throw new RuntimeException(
                    "SDK invocation error for " + constant, ite.getCause());
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to process SDK call for " + constant, e);
        }
    }

    /**
     * Uses reflection to read a private static field on reqCls:
     * ‣ fieldName (e.g. "QUERY_TYPE" or "EVENT_TYPE")
     * and, if present, adds it to `params` with the given jsonKey
     * (e.g. "queryType" or "eventType").
     *
     * @return true if the field was found and injected
     */
    private boolean tryInjectStatic(JsonObject params,
                                    Class<?> reqCls,
                                    String fieldName,
                                    String jsonKey)
            throws IllegalAccessException {
        try {
            Field f = reqCls.getDeclaredField(fieldName);
            f.setAccessible(true);
            Object val = f.get(null);
            if (val != null) {
                params.addProperty(jsonKey, val.toString());
                return true;
            }
        } catch (NoSuchFieldException ignored) {
        }
        return false;
    }
}
