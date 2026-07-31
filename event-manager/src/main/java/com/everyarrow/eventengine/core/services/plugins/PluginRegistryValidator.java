/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.plugins;

import com.everyarrow.eventengine.core.services.runtime.GenericProcessHandler;
import com.everyarrow.eventengine.spi.handler.ServiceCallHandler;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;

import java.util.List;

/**
 * Validates a plugin registry document against the plugin class loader.
 */
public class PluginRegistryValidator {

    public void validate(PluginRegistryDocument document, PluginClassLoader classLoader) {
        if (document == null) {
            throw new IllegalStateException("Plugin registry document must not be null");
        }

        if (document.getPlugin() == null) {
            throw new IllegalStateException("Plugin registry document is missing plugin metadata");
        }

        if (isBlank(document.getPlugin().getId())) {
            throw new IllegalStateException("Plugin registry document is missing plugin.id");
        }
        PluginPathSecurity.validatePluginId(document.getPlugin().getId());

        if (isBlank(document.getPlugin().getVersion())) {
            throw new IllegalStateException("Plugin registry document is missing plugin.version");
        }

        for (String resourcePath : document.getPlugin().getRegistryResources()) {
            PluginPathSecurity.normalizeRegistryResourcePath(resourcePath);
        }

        List<ServiceCallDefinition> services = document.getServices();
        if (services == null) {
            throw new IllegalStateException("Plugin registry document services must not be null");
        }

        for (ServiceCallDefinition def : services) {
            validateServiceDefinition(def, classLoader);
        }
    }

    private void validateServiceDefinition(ServiceCallDefinition def, PluginClassLoader classLoader) {
        if (def == null) {
            throw new IllegalStateException("Plugin service definition must not be null");
        }

        if (isBlank(def.getServiceType())) {
            throw new IllegalStateException("Plugin service definition is missing serviceType");
        }

        if (isHostBackedGenericFunction(def)) {
            return;
        }

        String handlerClassName = def.getHandlerClass();
        if (isBlank(handlerClassName)) {
            handlerClassName = def.getClassName();
        }

        if (isBlank(handlerClassName)) {
            throw new IllegalStateException("Plugin service definition is missing handler class");
        }

        try {
            Class<?> clazz = classLoader.loadClass(handlerClassName);

            if (!ServiceCallHandler.class.isAssignableFrom(clazz)) {
                throw new IllegalStateException("Declared plugin class does not implement ServiceCallHandler: " + handlerClassName);
            }

            if (clazz.isInterface() || java.lang.reflect.Modifier.isAbstract(clazz.getModifiers()) || clazz.isEnum()) {
                throw new IllegalStateException("Declared plugin handler must be concrete: " + handlerClassName);
            }

        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Declared plugin handler class not found: " + handlerClassName, e);
        }
    }

    private boolean isHostBackedGenericFunction(ServiceCallDefinition def) {
        String handlerClassName = firstNonBlank(def.getHandlerClass(), def.getClassName());

        if (isGenericProcessHandlerName(handlerClassName)) {
            return isDbBackedService(def);
        }

        return isBlank(handlerClassName) && isDbBackedService(def);
    }

    private boolean isDbBackedService(ServiceCallDefinition def) {
        String source = def.getSource();
        if (source != null && source.startsWith("db:")) {
            return true;
        }

        String serviceType = def.getServiceType();
        if (isBlank(serviceType)) {
            return false;
        }

        return serviceType.split("\\.").length == 3;
    }

    private boolean isGenericProcessHandlerName(String handlerClassName) {
        return GenericProcessHandler.class.getSimpleName().equals(handlerClassName)
                || GenericProcessHandler.class.getName().equals(handlerClassName);
    }

    private String firstNonBlank(String primary, String secondary) {
        if (!isBlank(primary)) {
            return primary;
        }
        return secondary;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
