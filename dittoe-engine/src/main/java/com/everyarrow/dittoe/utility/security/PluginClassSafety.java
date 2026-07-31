package com.everyarrow.dittoe.utility.security;

import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

import java.util.Map;

public final class PluginClassSafety {
    public static final String ALLOW_UNSAFE_CUSTOM_CLASS = "allowUnsafeCustomClass";
    private static final String ENGINE_PACKAGE_PREFIX = "com.everyarrow.dittoe.";

    private PluginClassSafety() {
    }

    public static String requireAllowedEngineClass(String className, Map<String, String> properties) throws ExecutionException {
        if (className == null || className.isBlank()) {
            throw new ExecutionException("Class name cannot be blank.");
        }
        if (className.startsWith(ENGINE_PACKAGE_PREFIX)) {
            return className;
        }
        if (Boolean.parseBoolean(properties.getOrDefault(ALLOW_UNSAFE_CUSTOM_CLASS, "false"))) {
            return className;
        }
        throw new ExecutionException("Custom class loading is disabled by default: " + className
                + ". Set " + ALLOW_UNSAFE_CUSTOM_CLASS + "=true only for trusted extension classpaths.");
    }
}
